package com.example.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class TestRunService {

    public enum Status {
        QUEUED,
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    public record TestRunView(
            String id,
            String featurePath,
            List<String> tags,
            List<String> names,
            List<Map<String, Object>> scenarios,
            Status status,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            Integer exitCode,
            String logPath,
            String errorMessage
    ) {}

    private static final Logger log = LoggerFactory.getLogger(TestRunService.class);
    private static final Path RUN_ROOT = Paths.get("target", "ui-runs");

    private final ExecutorService executor;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentMap<String, RunJob> jobs = new ConcurrentHashMap<>();
    private final AtomicInteger threadCounter = new AtomicInteger(0);

    public TestRunService() {
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "ui-test-runner-" + threadCounter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    public TestRunView startRun(Path featurePath, List<String> cucumberTags, List<String> cucumberNames, Map<String, String> envOverrides) throws IOException {
        Path normalized = featurePath == null ? null : featurePath.toAbsolutePath().normalize();
        if (normalized != null && !Files.exists(normalized)) {
            throw new IOException("Feature file does not exist: " + normalized);
        }
        Files.createDirectories(RUN_ROOT);
        String id = UUID.randomUUID().toString();
        Path jobDir = RUN_ROOT.resolve(id);
        Files.createDirectories(jobDir);
        Path logPath = jobDir.resolve("run.log");
        RunJob job = new RunJob(id, normalized, logPath, jobDir);
        if (cucumberTags != null && !cucumberTags.isEmpty()) {
            job.tags.addAll(cucumberTags);
        }
        if (cucumberNames != null && !cucumberNames.isEmpty()) {
            job.names.addAll(cucumberNames);
        }
        if (envOverrides != null && !envOverrides.isEmpty()) {
            job.envOverrides.putAll(envOverrides);
        }
        jobs.put(id, job);
        job.appendLog("Starting test run " + id);
        if (normalized != null) {
            job.appendLog("Feature: " + normalized);
        }
        if (!job.tags.isEmpty()) {
            job.appendLog("Tags: " + String.join(", ", job.tags));
        }
        if (!job.names.isEmpty()) {
            job.appendLog("Names: " + String.join(", ", job.names));
        }
        try {
            executor.submit(() -> execute(job));
        } catch (RejectedExecutionException ex) {
            job.failBeforeStart(ex);
            throw ex;
        }
        return job.view();
    }

    public Optional<TestRunView> getRun(String id) {
        RunJob job = jobs.get(id);
        return job == null ? Optional.empty() : Optional.of(job.view());
    }

    public List<TestRunView> listRuns() {
        List<RunJob> snapshot = new ArrayList<>(jobs.values());
        snapshot.sort(Comparator.comparing((RunJob j) -> j.createdAt).reversed());
        List<TestRunView> views = new ArrayList<>(snapshot.size());
        for (RunJob job : snapshot) {
            views.add(job.view());
        }
        return views;
    }

    public Optional<Path> getLogPath(String id) {
        RunJob job = jobs.get(id);
        return job == null ? Optional.empty() : Optional.of(job.logPath);
    }

    private void execute(RunJob job) {
        job.markRunning();
        resetAllureResults();
        List<String> command = new ArrayList<>();
        String mvnExecutable = findMavenExecutable();
        command.add(mvnExecutable);
        command.add("-q");
        command.add("test");
        command.add("-Dtest=runner.RunCucumberTest");
        if (job.featurePath != null) {
            command.add("-Dcucumber.features=" + job.featurePath);
        }
        if (!job.tags.isEmpty()) {
            command.add("-Dcucumber.filter.tags=" + String.join(" or ", job.tags));
        }
        if (!job.names.isEmpty()) {
            java.util.List<String> patterns = new java.util.ArrayList<>();
            for (String name : job.names) {
                patterns.add(toNamePattern(name));
            }
            command.add("-Dcucumber.filter.name=" + String.join(" or ", patterns));
        }
        command.add("-Dui.run.id=" + job.id);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(Paths.get("/"));
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(job.logPath.toFile()));
        Map<String, String> env = pb.environment();
        env.putIfAbsent("CUCUMBER_PUBLISH_ENABLED", "false");
        for (Map.Entry<String, String> entry : job.envOverrides.entrySet()) {
            env.put(entry.getKey(), entry.getValue());
        }

        try {
            Process process = pb.start();
            int exitCode = process.waitFor();
            job.finish(exitCode, null);
        } catch (IOException ex) {
            job.finish(-1, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            job.finish(-1, ex);
        } finally {
            captureReports(job);
        }
    }

    private void resetAllureResults() {
        try {
            Path dir = Paths.get("target", "allure-results");
            if (!Files.exists(dir)) {
                return;
            }
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ex) {
                            log.warn("Unable to delete {}", p, ex);
                        }
                    });
        } catch (IOException ex) {
            log.warn("Failed to reset Allure results", ex);
        }
    }

    private void captureReports(RunJob job) {
        try {
            Path report = Paths.get("target", "cucumber-report.json");
            if (!Files.exists(report)) {
                return;
            }
            Files.createDirectories(job.jobDir);
            Path dest = job.jobDir.resolve("cucumber-report.json");
            Files.copy(report, dest, StandardCopyOption.REPLACE_EXISTING);

            try (java.io.InputStream in = Files.newInputStream(report)) {
                JsonNode root = objectMapper.readTree(in);
                if (root != null && root.isArray()) {
                    java.util.List<ScenarioResult> newResults = new java.util.ArrayList<>();
                    for (JsonNode feature : root) {
                        String featureName = feature.path("name").asText(null);
                        JsonNode elements = feature.path("elements");
                        if (elements == null || !elements.isArray()) {
                            continue;
                        }
                        for (JsonNode scenario : elements) {
                            String scenarioName = scenario.path("name").asText(null);
                            String status = "PASSED";
                            String failingStep = null;
                            FailureText failure = new FailureText(null, java.util.List.of());
                            JsonNode steps = scenario.path("steps");
                            if (steps != null && steps.isArray()) {
                                for (JsonNode step : steps) {
                                    JsonNode result = step.path("result");
                                    String stepStatus = result.path("status").asText("");
                                    if ("failed".equalsIgnoreCase(stepStatus)) {
                                        status = "FAILED";
                                        failingStep = step.path("name").asText(null);
                                        failure = parseFailure(result.path("error_message").asText(null));
                                        break;
                                    }
                                }
                            }
                            if (!"PASSED".equals(status)) {
                                if (newResults.size() < 12) {
                                    newResults.add(new ScenarioResult(featureName, scenarioName, status, failingStep, failure.summary, failure.details));
                                }
                            }
                        }
                    }
                    synchronized (job.scenarioResults) {
                        job.scenarioResults.clear();
                        job.scenarioResults.addAll(newResults);
                    }
                }
            }
        } catch (Exception ex) {
            job.appendLog("Failed to capture cucumber report: " + ex.getMessage());
        }
    }

    private String findMavenExecutable() {
        Path wrapper = Paths.get("mvnw");
        if (Files.exists(wrapper) && Files.isExecutable(wrapper)) {
            return "./mvnw";
        }
        return "mvn";
    }

    private static FailureText parseFailure(String raw) {
        if (raw == null) return new FailureText(null, java.util.List.of());
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return new FailureText(null, java.util.List.of());
        String[] lines = trimmed.split("\r?\n");
        java.util.List<String> detailLines = new java.util.ArrayList<>();
        for (String line : lines) {
            String t = line.strip();
            if (t.isEmpty()) continue;
            detailLines.add(t);
            if (detailLines.size() >= 6) break;
        }
        String summary = detailLines.isEmpty() ? trimmed : detailLines.get(0);
        if (summary.length() > 280) {
            summary = summary.substring(0, 280) + "…";
        }
        return new FailureText(summary, detailLines);
    }

    private static String toNamePattern(String name) {
        if (name == null) return "";
        return "^" + Pattern.quote(name) + "$";
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private static final class FailureText {
        final String summary;
        final java.util.List<String> details;
        FailureText(String summary, java.util.List<String> details) {
            this.summary = summary;
            this.details = details == null ? java.util.List.of() : java.util.List.copyOf(details);
        }
    }

    private static final class ScenarioResult {
        final String feature;
        final String scenario;
        final String status;
        final String step;
        final String summary;
        final java.util.List<String> details;

        ScenarioResult(String feature, String scenario, String status, String step, String summary, java.util.List<String> details) {
            this.feature = feature;
            this.scenario = scenario;
            this.status = status;
            this.step = step;
            this.summary = summary;
            this.details = details == null ? java.util.List.of() : java.util.List.copyOf(details);
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new HashMap<>();
            if (feature != null) m.put("feature", feature);
            if (scenario != null) m.put("scenario", scenario);
            if (status != null) m.put("status", status);
            if (step != null) m.put("step", step);
            if (summary != null) m.put("summary", summary);
            if (!details.isEmpty()) m.put("details", details);
            return m;
        }
    }

    private static final class RunJob {
        final String id;
        final Instant createdAt = Instant.now();
        final Path featurePath;
        final Path logPath;
        final Path jobDir;
        final List<String> tags = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        final List<ScenarioResult> scenarioResults = new ArrayList<>();
        final Map<String, String> envOverrides = new ConcurrentHashMap<>();
        volatile Status status = Status.QUEUED;
        volatile Instant startedAt;
        volatile Instant completedAt;
        volatile Integer exitCode;
        volatile String errorMessage;

        private RunJob(String id, Path featurePath, Path logPath, Path jobDir) {
            this.id = Objects.requireNonNull(id, "id");
            this.featurePath = featurePath;
            this.logPath = Objects.requireNonNull(logPath, "logPath");
            this.jobDir = Objects.requireNonNull(jobDir, "jobDir");
        }

        void appendLog(String message) {
            String line = message + System.lineSeparator();
            try {
                Files.writeString(logPath, line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ex) {
                log.warn("Failed to write log line for job {}", id, ex);
            }
        }

        void markRunning() {
            this.status = Status.RUNNING;
            this.startedAt = Instant.now();
            appendLog("Command started at " + this.startedAt);
        }

        void finish(int exitCode, Exception error) {
            this.exitCode = exitCode;
            this.completedAt = Instant.now();
            if (error != null) {
                this.status = Status.FAILED;
                this.errorMessage = error.getMessage();
                appendLog("Run failed: " + error.getMessage());
            } else if (exitCode == 0) {
                this.status = Status.SUCCEEDED;
                appendLog("Run finished successfully with exit code 0");
            } else {
                this.status = Status.FAILED;
                appendLog("Run finished with non-zero exit code " + exitCode);
            }
            appendLog("Command finished at " + this.completedAt);
        }

        void failBeforeStart(Exception error) {
            this.status = Status.FAILED;
            this.completedAt = Instant.now();
            this.errorMessage = error.getMessage();
            appendLog("Failed to start run: " + error.getMessage());
        }

        TestRunView view() {
            List<Map<String, Object>> scenarioMaps;
            synchronized (scenarioResults) {
                scenarioMaps = scenarioResults.stream().map(ScenarioResult::toMap).collect(Collectors.toList());
            }
            return new TestRunView(
                    id,
                    featurePath == null ? null : featurePath.toString(),
                    List.copyOf(tags),
                    List.copyOf(names),
                    scenarioMaps,
                    status,
                    createdAt,
                    startedAt,
                    completedAt,
                    exitCode,
                    logPath.toString(),
                    errorMessage
            );
        }
    }
}
