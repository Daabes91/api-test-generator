package com.example.web;

import com.example.curl.ApiCall;
import com.example.curl.CurlParser;
import com.example.generator.FeatureGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.support.TestRunService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Map;

@RestController
public class GenerateController {

    private static final Logger log = LoggerFactory.getLogger(GenerateController.class);

    private final TestRunService testRunService;

    public GenerateController(TestRunService testRunService) {
        this.testRunService = testRunService;
    }

    public record GenerateRequest(String curl,
                                  String notes,
                                  String tokenEnvVar,
                                  String outDir,
                                  String extraHeaders,
                                  String queryParams,
                                  Integer successStatus,
                                  String datasetName,
                                  String manualJson,
                                  String chain,
                                  Boolean includeNegatives,
                                  Boolean includeTypeNegatives,
                                  Boolean includeNullNegatives,
                                  Boolean includeIdempotency,
                                  Boolean includePagination,
                                  String featureName,
                                  String assertions,
                                  Integer expStatusPositive,
                                  Integer expStatusIdem,
                                  Integer expStatusAuth,
                                  Integer expStatusNegative,
                                  String expJsonPositive,
                                  String expJsonIdem,
                                  String expJsonAuth,
                                  String expJsonNegative,
                                  String requiredFields,
                                  String lengthRules) {}
    public record TemplateRequest(String curl, String manualJson, String datasetName) {}
    public record UpdateFeatureRequest(String path, String content) {}

    public record TestRunRequest(String featurePath, List<String> tags, List<String> names, Map<String, String> env) {}

    @PostMapping(value = "/api/generate", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> generateForm(@ModelAttribute GenerateRequest req) {
        return handle(req, null);
    }

    @PostMapping(value = "/api/generate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> generateJson(@RequestBody GenerateRequest req) {
        return handle(req, null);
    }

    @PostMapping(value = "/api/feature/update", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateFeature(@RequestBody UpdateFeatureRequest req) {
        if (req == null || req.path == null || req.path().isBlank()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("ok", false, "error", "Missing feature path"));
        }
        if (req.content == null) {
            return ResponseEntity.badRequest().body(java.util.Map.of("ok", false, "error", "Missing feature content"));
        }
        try {
            java.nio.file.Path featurePath = java.nio.file.Paths.get(req.path()).normalize();
            if (!featurePath.isAbsolute()) {
                featurePath = java.nio.file.Paths.get("").toAbsolutePath().resolve(featurePath).normalize();
            }
            java.nio.file.Files.createDirectories(featurePath.getParent());
            java.nio.file.Files.writeString(featurePath, req.content(), java.nio.charset.StandardCharsets.UTF_8);
            return ResponseEntity.ok(java.util.Map.of("ok", true, "path", featurePath.toString()));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(java.util.Map.of("ok", false, "error", ex.getMessage()));
        }
    }

    @PostMapping(value = "/api/tests/run", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> startTestRun(@RequestBody TestRunRequest req) {
        if (req == null) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "Request body is required"));
        }
        Path featurePath = normalizeFeaturePath(req.featurePath());
        List<String> tags = sanitizeTags(req.tags());
        List<String> names = sanitizeNames(req.names());
        Map<String, String> env = sanitizeEnv(req.env());
        try {
            TestRunService.TestRunView view = testRunService.startRun(featurePath, tags, names, env);
            return ResponseEntity.accepted().body(view);
        } catch (IOException ex) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("ok", false, "error", ex.getMessage()));
        }
    }

    @GetMapping(value = "/api/tests", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listTestRuns() {
        return ResponseEntity.ok(testRunService.listRuns());
    }

    @GetMapping(value = "/api/tests/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getTestRun(@PathVariable("id") String id) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "Run id is required"));
        }
        Optional<TestRunService.TestRunView> view = testRunService.getRun(id.trim());
        return view.<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("ok", false, "error", "Run not found")));
    }

    @GetMapping(value = "/api/tests/{id}/log", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<?> getTestRunLog(@PathVariable("id") String id) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body("Run id is required");
        }
        Optional<Path> pathOpt = testRunService.getLogPath(id.trim());
        if (pathOpt.isEmpty()) {
            return ResponseEntity.status(404).body("Run not found");
        }
        Path logPath = pathOpt.get();
        try {
            if (!Files.exists(logPath)) {
                return ResponseEntity.status(404).body("Log file not found");
            }
            String content = Files.readString(logPath);
            return ResponseEntity.ok(content);
        } catch (IOException ex) {
            return ResponseEntity.internalServerError().body("Unable to read log: " + ex.getMessage());
        }
    }

    @PostMapping(value = "/api/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> generateMultipart(
            @RequestParam("curl") String curl,
            @RequestParam(value = "notes", required = false) String notes,
            @RequestParam(value = "tokenEnvVar", required = false) String tokenEnvVar,
            @RequestParam(value = "outDir", required = false) String outDir,
            @RequestParam(value = "extraHeaders", required = false) String extraHeaders,
            @RequestParam(value = "queryParams", required = false) String queryParams,
            @RequestParam(value = "successStatus", required = false) Integer successStatus,
            @RequestParam(value = "datasetName", required = false) String datasetName,
            @RequestParam(value = "manualJson", required = false) String manualJson,
            @RequestParam(value = "includeNegatives", required = false) Boolean includeNegatives,
            @RequestParam(value = "includeTypeNegatives", required = false) Boolean includeTypeNegatives,
            @RequestParam(value = "includeNullNegatives", required = false) Boolean includeNullNegatives,
            @RequestParam(value = "includeIdempotency", required = false) Boolean includeIdempotency,
            @RequestParam(value = "includePagination", required = false) Boolean includePagination,
            @RequestParam(value = "featureName", required = false) String featureName,
            @RequestParam(value = "assertions", required = false) String assertions,
            @RequestParam(value = "expStatusPositive", required = false) Integer expStatusPositive,
            @RequestParam(value = "expStatusIdem", required = false) Integer expStatusIdem,
            @RequestParam(value = "expStatusAuth", required = false) Integer expStatusAuth,
            @RequestParam(value = "expStatusNegative", required = false) Integer expStatusNegative,
            @RequestParam(value = "expJsonPositive", required = false) String expJsonPositive,
            @RequestParam(value = "expJsonIdem", required = false) String expJsonIdem,
            @RequestParam(value = "expJsonAuth", required = false) String expJsonAuth,
            @RequestParam(value = "expJsonNegative", required = false) String expJsonNegative,
            @RequestParam(value = "requiredFields", required = false) String requiredFields,
            @RequestParam(value = "lengthRules", required = false) String lengthRules,
            @RequestPart(value = "dataset", required = false) MultipartFile dataset
    ) {
        GenerateRequest req = new GenerateRequest(curl, notes, tokenEnvVar, outDir, extraHeaders, queryParams, successStatus, datasetName, manualJson, null,
                includeNegatives, includeTypeNegatives, includeNullNegatives, includeIdempotency, includePagination, featureName, assertions,
                expStatusPositive, expStatusIdem, expStatusAuth, expStatusNegative,
                expJsonPositive, expJsonIdem, expJsonAuth, expJsonNegative,
                requiredFields,
                lengthRules);
        return handle(req, dataset);
    }

    // Accept raw text or binary bodies where clients post only the curl string
    @PostMapping(value = "/api/generate", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<?> generateTextPlain(@RequestBody String body) {
        if (body == null || body.isBlank()) {
            return ResponseEntity.status(415).body(java.util.Map.of("ok", false, "error", "Empty body for text/plain; expected a curl command"));
        }
        GenerateRequest req = new GenerateRequest(body.trim(), null, null, null, null, null, null, null, null, null,
                true, true, true, true, true, null, null,
                null, null, null, null, null, null, null, null, null, null);
        return handle(req, null);
    }

    @PostMapping(value = "/api/generate", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<?> generateOctet(@RequestBody byte[] raw) {
        if (raw == null || raw.length == 0) {
            return ResponseEntity.status(415).body(java.util.Map.of("ok", false, "error", "Empty body for application/octet-stream; expected a curl command"));
        }
        String s = new String(raw, java.nio.charset.StandardCharsets.UTF_8).trim();
        if (s.isEmpty()) {
            return ResponseEntity.status(415).body(java.util.Map.of("ok", false, "error", "Body could not be parsed as UTF-8 text"));
        }
        GenerateRequest req = new GenerateRequest(s, null, null, null, null, null, null, null, null, null,
                true, true, true, true, true, null, null,
                null, null, null, null, null, null, null, null, null, null);
        return handle(req, null);
    }

    // Fallback: accept any Content-Type to avoid 415s from unusual clients
    @PostMapping(value = "/api/generate", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<?> generateAny(
            @RequestParam(value = "curl", required = false) String curl,
            @RequestParam(value = "notes", required = false) String notes,
            @RequestParam(value = "tokenEnvVar", required = false) String tokenEnvVar,
            @RequestParam(value = "outDir", required = false) String outDir,
            @RequestParam(value = "extraHeaders", required = false) String extraHeaders,
            @RequestParam(value = "queryParams", required = false) String queryParams,
            @RequestParam(value = "successStatus", required = false) Integer successStatus,
            @RequestParam(value = "datasetName", required = false) String datasetName,
            @RequestParam(value = "manualJson", required = false) String manualJson,
            @RequestParam(value = "includeNegatives", required = false) Boolean includeNegatives,
            @RequestParam(value = "includeTypeNegatives", required = false) Boolean includeTypeNegatives,
            @RequestParam(value = "includeNullNegatives", required = false) Boolean includeNullNegatives,
            @RequestParam(value = "includeIdempotency", required = false) Boolean includeIdempotency,
            @RequestParam(value = "includePagination", required = false) Boolean includePagination,
            @RequestParam(value = "featureName", required = false) String featureName,
            @RequestParam(value = "assertions", required = false) String assertions,
            @RequestParam(value = "expStatusPositive", required = false) Integer expStatusPositive,
            @RequestParam(value = "expStatusIdem", required = false) Integer expStatusIdem,
            @RequestParam(value = "expStatusAuth", required = false) Integer expStatusAuth,
            @RequestParam(value = "expStatusNegative", required = false) Integer expStatusNegative,
            @RequestParam(value = "expJsonPositive", required = false) String expJsonPositive,
            @RequestParam(value = "expJsonIdem", required = false) String expJsonIdem,
            @RequestParam(value = "expJsonAuth", required = false) String expJsonAuth,
            @RequestParam(value = "expJsonNegative", required = false) String expJsonNegative,
            @RequestParam(value = "requiredFields", required = false) String requiredFields,
            @RequestParam(value = "lengthRules", required = false) String lengthRules,
            @RequestParam(value = "dataset", required = false) MultipartFile dataset
    ) {
        if (curl == null || curl.isBlank()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("ok", false, "error", "Missing required 'curl' field"));
        }
        GenerateRequest req = new GenerateRequest(curl, notes, tokenEnvVar, outDir, extraHeaders, queryParams, successStatus, datasetName, manualJson, null,
                includeNegatives, includeTypeNegatives, includeNullNegatives, includeIdempotency, includePagination, featureName, assertions,
                expStatusPositive, expStatusIdem, expStatusAuth, expStatusNegative,
                expJsonPositive, expJsonIdem, expJsonAuth, expJsonNegative,
                requiredFields,
                lengthRules);
        return handle(req, dataset);
    }

    private ResponseEntity<?> handle(GenerateRequest req, MultipartFile dataset) {
        Map<String, Object> resp = new HashMap<>();
        try {
            if (req == null || req.curl == null || req.curl.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Field 'curl' is required"));
            }
            String tokenEnv = (req.tokenEnvVar == null || req.tokenEnvVar.isBlank()) ? "API_TOKEN" : req.tokenEnvVar;
            ApiCall call = CurlParser.parse(req.curl);
            // allow manual JSON to override cURL body
            if (req.manualJson != null && !req.manualJson.isBlank()) {
                call.setBody(req.manualJson);
            }
            var headers = parseHeaderLines(req.extraHeaders);
            var query = parseQueryLines(req.queryParams);
            var assertionSteps = buildAssertionSteps(req.assertions);
            var headerAssertions = buildHeaderAssertions(call);
            java.util.List<String> assertionsPositive = combineScenarioAssertions(headerAssertions, assertionSteps, req.expJsonPositive, true);
            java.util.List<String> assertionsIdem = combineScenarioAssertions(headerAssertions, assertionSteps, req.expJsonIdem, true);
            java.util.List<String> assertionsAuth = combineScenarioAssertions(headerAssertions, assertionSteps, req.expJsonAuth, false);
            java.util.List<String> assertionsNegative = combineScenarioAssertions(headerAssertions, assertionSteps, req.expJsonNegative, false);
            Integer success = (req.successStatus == null || req.successStatus <= 0) ? 200 : req.successStatus;
            java.util.List<ChainStep> chainSteps = parseChain(req.chain);
            String datasetCsvPath = null;
            int datasetRows = 0;
            java.util.List<String> datasetHeaders = java.util.List.of();
            java.util.Set<String> datasetHeaderSet = java.util.Set.of();
            if (dataset != null && !dataset.isEmpty()) {
                String fileName = (req.datasetName == null || req.datasetName.isBlank()) ? defaultDatasetName(call) : sanitizeName(req.datasetName);
                java.nio.file.Path outDir = java.nio.file.Paths.get("src/test/resources/datasets");
                java.nio.file.Files.createDirectories(outDir);
                java.nio.file.Path out = outDir.resolve(fileName + ".csv");
                String lc = dataset.getOriginalFilename() == null ? "" : dataset.getOriginalFilename().toLowerCase();
                if (lc.endsWith(".xlsx")) {
                    String csv = com.example.support.DatasetUtil.xlsxToCsv(dataset.getInputStream());
                    java.nio.file.Files.writeString(out, csv);
                    byte[] bytes = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    datasetRows = com.example.support.DatasetUtil.countCsvRows(new java.io.ByteArrayInputStream(bytes));
                    datasetHeaders = com.example.support.DatasetUtil.parseCsvHeader(new java.io.ByteArrayInputStream(bytes));
                } else {
                    // assume CSV
                    byte[] bytes = dataset.getBytes();
                    java.nio.file.Files.write(out, bytes);
                    datasetRows = com.example.support.DatasetUtil.countCsvRows(new java.io.ByteArrayInputStream(bytes));
                    datasetHeaders = com.example.support.DatasetUtil.parseCsvHeader(new java.io.ByteArrayInputStream(bytes));
                }
                datasetCsvPath = "datasets/" + out.getFileName().toString();
                datasetHeaderSet = filterReservedHeaders(datasetHeaders);
            }

            boolean incNeg = req.includeNegatives == null ? true : req.includeNegatives;
            boolean incTypeNeg = req.includeTypeNegatives == null ? incNeg : req.includeTypeNegatives;
            boolean incNullNeg = req.includeNullNegatives == null ? incNeg : req.includeNullNegatives;
            boolean incIdem = req.includeIdempotency == null ? true : req.includeIdempotency;
            boolean incPag = req.includePagination == null ? true : req.includePagination;
            java.util.List<String> requiredPaths = parseRequiredPaths(req.requiredFields);
            java.util.List<String> allAssertions = mergeAssertions(
                    mergeAssertions(assertionsPositive, assertionsIdem),
                    mergeAssertions(assertionsAuth, assertionsNegative)
            );

            String feature = FeatureGenerator.generateFeature(
                    call, tokenEnv, headers, query, success,
                    datasetCsvPath, datasetRows,
                    incNeg, incIdem, incPag,
                    assertionsPositive,
                    assertionsIdem,
                    assertionsAuth,
                    assertionsNegative,
                    req.expStatusPositive, req.expStatusIdem, req.expStatusAuth, req.expStatusNegative,
                    req.expJsonPositive, req.expJsonIdem, req.expJsonAuth, req.expJsonNegative,
                    requiredPaths,
                    incTypeNeg,
                    incNullNeg,
                    datasetHeaderSet
            );

            if (!chainSteps.isEmpty()) {
                feature += buildChainScenario(chainSteps, success);
            }

            // Append length-rule negative scenarios (strings/arrays)
            java.util.List<String[]> lengthRules = parseLengthRules(req.lengthRules);
            if (!lengthRules.isEmpty() && call.getBody() != null && !call.getBody().isBlank()) {
                try {
                    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    var original = mapper.readTree(call.getBody());
                    int negStatus = (req.expStatusNegative != null) ? req.expStatusNegative : 400;
                    StringBuilder extra = new StringBuilder();
                    for (String[] rule : lengthRules) {
                        String pathRule = rule[0];
                        Integer min = rule[1] != null ? Integer.valueOf(rule[1]) : null;
                        Integer max = rule[2] != null ? Integer.valueOf(rule[2]) : null;
                        // too short case
                        if (min != null && min > 0) {
                            var mutated = original.deepCopy();
                            if (applyLength(mutated, pathRule, Math.max(0, min - 1))) {
                                extra.append("\n  @negative\n  Scenario: invalid length for '").append(pathRule).append("' (too short)\n");
                                extra.append("    When I ").append(call.getMethod().name()).append(" to \"")
                                        .append(FeatureGenerator.pathOf(call.getUrl())).append("\" with json body:\n      \"\"\"\n")
                                        .append(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mutated)).append("\n      \"\"\"\n");
                                extra.append("    Then the response status should be ").append(negStatus).append("\n");
                                // expected json for negative
                                if (req.expJsonNegative != null && !req.expJsonNegative.isBlank()) {
                                    extra.append("    Then the response json should equal:\n      \"\"\"\n")
                                            .append(req.expJsonNegative).append("\n      \"\"\"\n");
                                }
                                // global assertions
                                for (String s : allAssertions) extra.append("    ").append(s).append("\n");
                            }
                        }
                        // too long case
                        if (max != null) {
                            var mutated = original.deepCopy();
                            if (applyLength(mutated, pathRule, max + 1)) {
                                extra.append("\n  @negative\n  Scenario: invalid length for '").append(pathRule).append("' (too long)\n");
                                extra.append("    When I ").append(call.getMethod().name()).append(" to \"")
                                        .append(FeatureGenerator.pathOf(call.getUrl())).append("\" with json body:\n      \"\"\"\n")
                                        .append(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mutated)).append("\n      \"\"\"\n");
                                extra.append("    Then the response status should be ").append(negStatus).append("\n");
                                if (req.expJsonNegative != null && !req.expJsonNegative.isBlank()) {
                                    extra.append("    Then the response json should equal:\n      \"\"\"\n")
                                            .append(req.expJsonNegative).append("\n      \"\"\"\n");
                                }
                                for (String s : allAssertions) extra.append("    ").append(s).append("\n");
                            }
                        }
                    }
                    feature = feature + extra.toString();
                } catch (Exception ignore) {}
            }

            String path = java.net.URI.create(call.getUrl()).getPath();
            String name;
            if (req.featureName != null && !req.featureName.isBlank()) {
                name = sanitizeName(req.featureName) + ".feature";
            } else {
                name = call.getMethod().name().toLowerCase() + "_" + FeatureGenerator.slugify(path) + ".feature";
            }
            Path outDir = Paths.get((req.outDir == null || req.outDir.isBlank()) ? "src/test/resources/features/generated" : req.outDir);
            Files.createDirectories(outDir);
            Path out = outDir.resolve(name);
            Files.writeString(out, feature);
            triggerAutoPush();

            resp.put("ok", true);
            resp.put("path", out.toString());
            resp.put("featurePreview", feature);
            if (allAssertions != null && !allAssertions.isEmpty()) {
                resp.put("assertionsApplied", allAssertions);
            }
            // Include required paths summary if provided
            java.util.List<String> reqPaths = parseRequiredPaths(req.requiredFields);
            if (reqPaths != null && !reqPaths.isEmpty()) {
                resp.put("requiredFieldsApplied", reqPaths);
            }
            java.util.List<String[]> lengthApplied = parseLengthRules(req.lengthRules);
            if (lengthApplied != null && !lengthApplied.isEmpty()) {
                java.util.List<String> lr = new java.util.ArrayList<>();
                for (String[] r : lengthApplied) lr.add(r[0] + "|" + (r[1]==null?"":r[1]) + "|" + (r[2]==null?"":r[2]));
                resp.put("lengthRulesApplied", lr);
            }
            // Include per-scenario expected overrides summary
            java.util.Map<String,Object> exp = new java.util.LinkedHashMap<>();
            if (req.expStatusPositive != null) exp.put("positiveStatus", req.expStatusPositive);
            if (req.expStatusIdem != null) exp.put("idempotencyStatus", req.expStatusIdem);
            if (req.expStatusAuth != null) exp.put("authStatus", req.expStatusAuth);
            if (req.expStatusNegative != null) exp.put("negativeStatus", req.expStatusNegative);
            if (req.expJsonPositive != null && !req.expJsonPositive.isBlank()) exp.put("positiveJson", req.expJsonPositive);
            if (req.expJsonIdem != null && !req.expJsonIdem.isBlank()) exp.put("idempotencyJson", req.expJsonIdem);
            if (req.expJsonAuth != null && !req.expJsonAuth.isBlank()) exp.put("authJson", req.expJsonAuth);
            if (req.expJsonNegative != null && !req.expJsonNegative.isBlank()) exp.put("negativeJson", req.expJsonNegative);
            if (!exp.isEmpty()) resp.put("expectedOverrides", exp);
            if (datasetCsvPath != null) {
                resp.put("datasetPath", datasetCsvPath);
                resp.put("datasetRows", datasetRows);
                resp.put("datasetHeaders", datasetHeaders);
                try {
                    String jsonBody = call.getBody();
                    if (jsonBody != null && !jsonBody.isBlank()) {
                        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        var root = mapper.readTree(jsonBody);
                        if (root.isObject()) {
                            java.util.Set<String> jsonKeys = new java.util.LinkedHashSet<>();
                            flattenJsonKeys((com.fasterxml.jackson.databind.node.ObjectNode) root, "", jsonKeys);
                            java.util.Set<String> headerSet = filterReservedHeaders(datasetHeaders);
                            java.util.Set<String> missing = new java.util.LinkedHashSet<>(jsonKeys);
                            missing.removeAll(headerSet);
                            java.util.Set<String> extra = new java.util.LinkedHashSet<>(headerSet);
                            extra.removeAll(jsonKeys);
                            if (!missing.isEmpty()) resp.put("datasetMissingHeaders", missing);
                            if (!extra.isEmpty()) resp.put("datasetExtraHeaders", extra);

                            // Type validation: compare first non-empty CSV value type with JSON field type
                            byte[] csvBytes;
                            if (dataset.getOriginalFilename() != null && dataset.getOriginalFilename().toLowerCase().endsWith(".xlsx")) {
                                // already wrote csv to disk; read it back
                                java.nio.file.Path datasetsDir = java.nio.file.Paths.get("src/test/resources");
                                java.nio.file.Path filePath = datasetsDir.resolve(datasetCsvPath);
                                csvBytes = java.nio.file.Files.readAllBytes(filePath);
                            } else {
                                // csvBytes were written; re-read
                                java.nio.file.Path datasetsDir = java.nio.file.Paths.get("src/test/resources");
                                java.nio.file.Path filePath = datasetsDir.resolve(datasetCsvPath);
                                csvBytes = java.nio.file.Files.readAllBytes(filePath);
                            }
                            var types = com.example.support.DatasetUtil.firstValueTypes(new java.io.ByteArrayInputStream(csvBytes));
                            java.util.List<java.util.Map<String,String>> mismatches = new java.util.ArrayList<>();
                            for (String key : jsonKeys) {
                                if (!types.containsKey(key)) continue;
                                String csvType = types.get(key);
                                if (csvType == null) continue;
                                String expected;
                                com.fasterxml.jackson.databind.JsonNode n = lookupByPath(root, key);
                                if (n == null || n.isNull()) continue;
                                if (n.isBoolean()) expected = "boolean";
                                else if (n.isIntegralNumber()) expected = "integer";
                                else if (n.isFloatingPointNumber()) expected = "number";
                                else if (n.isArray() || n.isObject()) expected = "json";
                                else expected = "string";
                                // only flag strong mismatches: boolean/integer/number
                                if ((expected.equals("boolean") && !csvType.equals("boolean")) ||
                                    (expected.equals("integer") && !csvType.equals("integer")) ||
                                    (expected.equals("number") && !(csvType.equals("integer") || csvType.equals("number")))) {
                                    mismatches.add(java.util.Map.of("field", key, "expected", expected, "actual", csvType));
                                }
                            }
                            if (!mismatches.isEmpty()) resp.put("datasetTypeMismatches", mismatches);
                        }
                    }
                } catch (Exception ignore) {}
            }
            // Warn if cURL headers (excluding Authorization) were not included by user extraHeaders
            java.util.Set<String> curlHeaderNames = new java.util.LinkedHashSet<>();
            for (var e : call.getHeaders().entrySet()) {
                if (!e.getKey().equalsIgnoreCase("Authorization")) curlHeaderNames.add(e.getKey());
            }
            java.util.Set<String> userHeaderNames = new java.util.LinkedHashSet<>();
            for (var hv : headers) userHeaderNames.add(hv[0]);
            java.util.Set<String> missingFromCurl = new java.util.LinkedHashSet<>(curlHeaderNames);
            missingFromCurl.removeAll(userHeaderNames);
            if (!missingFromCurl.isEmpty()) resp.put("missingHeadersFromCurl", missingFromCurl);
            return ResponseEntity.ok(resp);
        } catch (Exception ex) {
            resp.put("ok", false);
            resp.put("error", ex.getMessage());
            return ResponseEntity.badRequest().body(resp);
        }
    }

    private static class ChainStep {
        public String method;
        public String path;
        public String body;
        public String rememberPath;
        public String rememberKey;
    }

    private static java.util.List<ChainStep> parseChain(String raw) {
        java.util.List<ChainStep> out = new java.util.ArrayList<>();
        if (raw == null || raw.isBlank()) return out;
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(raw);
            if (node != null && node.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode n : node) {
                    ChainStep cs = new ChainStep();
                    cs.method = asText(n.get("method"));
                    cs.path = asText(n.get("path"));
                    cs.body = asText(n.get("body"));
                    cs.rememberPath = asText(n.get("rememberPath"));
                    cs.rememberKey = asText(n.get("rememberKey"));
                    if (cs.method != null && !cs.method.isBlank() && cs.path != null && !cs.path.isBlank()) {
                        out.add(cs);
                    }
                }
            }
        } catch (Exception ignore) {}
        return out;
    }

    private static String asText(com.fasterxml.jackson.databind.JsonNode n) {
        return (n == null || n.isNull()) ? null : n.asText();
    }

    private static String buildChainScenario(java.util.List<ChainStep> steps, int successStatus) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n  @integration\n  Scenario: Integration flow\n");
        for (ChainStep s : steps) {
            String method = (s.method == null ? "GET" : s.method.trim().toUpperCase());
            String path = s.path == null ? "/" : s.path;
            if (s.body != null && !s.body.isBlank()) {
                sb.append("    When I ").append(method).append(" to \"").append(path).append("\" with json body:\n");
                sb.append("      \"\"\"\n").append(s.body).append("\n      \"\"\"\n");
            } else {
                sb.append("    When I ").append(method).append(" to \"").append(path).append("\"\n");
            }
            sb.append("    Then the response status should be ").append(successStatus).append("\n");
            if (s.rememberPath != null && !s.rememberPath.isBlank() && s.rememberKey != null && !s.rememberKey.isBlank()) {
                sb.append("    Then remember json path \"").append(s.rememberPath.replace("\"","\\\"")).append("\" as \"").append(s.rememberKey.replace("\"","\\\"")).append("\"\n");
            }
        }
        return sb.toString();
    }

    private static String defaultDatasetName(ApiCall call) {
        String method = call.getMethod().name().toLowerCase();
        String slug = com.example.generator.FeatureGenerator.slugify(java.net.URI.create(call.getUrl()).getPath());
        return method + "_" + (slug.isBlank() ? "root" : slug);
    }
    private static String sanitizeName(String s){
        return s == null ? "dataset" : s.toLowerCase().replaceAll("[^a-z0-9_-]+","_");
    }

    private static java.util.List<String[]> parseHeaderLines(String raw){
        java.util.List<String[]> list = new java.util.ArrayList<>();
        if (raw == null) return list;
        for (String line : raw.split("\n")){
            if (line == null) continue;
            String t = line.trim();
            if (t.isEmpty()) continue;
            int idx = t.indexOf(":");
            if (idx <= 0) continue;
            String name = t.substring(0, idx).trim();
            String value = sanitizeHeaderValue(t.substring(idx+1).trim());
            if (!name.isEmpty() && !value.isEmpty()) list.add(new String[]{name, value});
        }
        return list;
    }

    private static String sanitizeHeaderValue(String v) {
        String cleaned = v == null ? "" : v.replace("\r", "");
        // Strip common trailing artifacts from pasted curl headers
        cleaned = cleaned.replaceAll("\\s*'\\s*\\\\\\\"\\s*$", ""); // trailing ' \"
        cleaned = cleaned.replaceAll("\\s*'\\s*\\\\\\s*$", "");         // trailing ' \\
        cleaned = cleaned.replaceAll("\\s*\\\\\\\"\\s*$", "");         // trailing \"
        cleaned = cleaned.replaceAll("\\s*'\\s*$", "");                       // trailing '
        cleaned = cleaned.replaceAll("\\s*\"\\s*$", "");                     // trailing "
        cleaned = cleaned.replaceAll("\\s*\\\\\\s*$", "");                 // trailing \\
        return cleaned;
    }

    private static java.util.List<String[]> parseQueryLines(String raw){
        java.util.List<String[]> list = new java.util.ArrayList<>();
        if (raw == null) return list;
        for (String line : raw.split("\n")){
            if (line == null) continue;
            String t = line.trim();
            if (t.isEmpty()) continue;
            int idx = t.indexOf("=");
            if (idx <= 0) continue;
            String name = t.substring(0, idx).trim();
            String value = t.substring(idx+1).trim();
            if (!name.isEmpty()) list.add(new String[]{name, value});
        }
        return list;
    }

    private static Path normalizeFeaturePath(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        Path pathValue = Paths.get(trimmed);
        if (!pathValue.isAbsolute()) {
            pathValue = Paths.get("").toAbsolutePath().resolve(pathValue).normalize();
        } else {
            pathValue = pathValue.normalize();
        }
        return pathValue;
    }

    private static List<String> sanitizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        for (String tag : tags) {
            if (tag == null) continue;
            String trimmed = tag.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    private static List<String> sanitizeNames(List<String> names) {
        if (names == null || names.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        for (String name : names) {
            if (name == null) continue;
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    private static Map<String, String> sanitizeEnv(Map<String, String> env) {
        if (env == null || env.isEmpty()) return Map.of();
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            if (entry.getKey() == null) continue;
            String key = entry.getKey().trim();
            if (key.isEmpty()) continue;
            String value = entry.getValue();
            out.put(key, value == null ? "" : value);
        }
        return out;
    }

    public record SuggestRequest(String curl) {}
    @PostMapping(value = "/api/suggest", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> suggest(@RequestBody SuggestRequest req){
        if (req == null || req.curl == null || req.curl.isBlank()){
            return ResponseEntity.badRequest().body(java.util.Map.of("error","Field 'curl' is required"));
        }
        try{
            var call = com.example.curl.CurlParser.parse(req.curl);
            java.util.Map<String,Object> out = new java.util.HashMap<>();
            out.put("method", call.getMethod().name());
            out.put("url", call.getUrl());
            out.put("path", java.net.URI.create(call.getUrl()).getPath());
            out.put("body", call.getBody());
            out.put("hasBearer", "Bearer".equalsIgnoreCase(call.getAuthType()));
            // headers excluding Authorization, with redaction for sensitive names
            java.util.List<java.util.Map<String,String>> headers = new java.util.ArrayList<>();
            for (var e: call.getHeaders().entrySet()){
                String name = e.getKey();
                if (name.equalsIgnoreCase("Authorization")) continue;
                String value = e.getValue();
                if (isSensitive(name)) value = "REDACTED";
                headers.add(java.util.Map.of("name", name, "value", value));
            }
            out.put("headers", headers);
            // query params from URL
            java.util.List<java.util.Map<String,String>> query = new java.util.ArrayList<>();
            String q = java.net.URI.create(call.getUrl()).getQuery();
            if (q != null && !q.isBlank()){
                for (String part : q.split("&")){
                    if (part.isBlank()) continue;
                    int idx = part.indexOf('=');
                    if (idx > 0){
                        String k = java.net.URLDecoder.decode(part.substring(0, idx), java.nio.charset.StandardCharsets.UTF_8);
                        String v = java.net.URLDecoder.decode(part.substring(idx+1), java.nio.charset.StandardCharsets.UTF_8);
                        query.add(java.util.Map.of("name", k, "value", v));
                    } else {
                        String k = java.net.URLDecoder.decode(part, java.nio.charset.StandardCharsets.UTF_8);
                        query.add(java.util.Map.of("name", k, "value", ""));
                    }
                }
            }
            out.put("query", query);
            return ResponseEntity.ok(out);
        }catch(Exception ex){
            return ResponseEntity.badRequest().body(java.util.Map.of("error", ex.getMessage()));
        }
    }

    private static boolean isSensitive(String name){
        String n = name.toLowerCase();
        return n.contains("authorization") || n.contains("cookie") || n.contains("x-api-key") || n.equals("api-key");
    }

    @PostMapping(value = "/api/template/dataset.csv", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<byte[]> datasetCsvTemplate(@ModelAttribute TemplateRequest req) {
        try {
            var headers = inferTemplateHeaders(req);
            var rows = buildScenarioRows(headers, req);
            StringBuilder sb = new StringBuilder();
            sb.append(String.join(",", headers)).append("\n");
            for (var row : rows) {
                java.util.List<String> escaped = new java.util.ArrayList<>();
                for (String cell : row) escaped.add(escapeCsvVal(cell));
                sb.append(String.join(",", escaped)).append("\n");
            }
            byte[] bytes = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String name = sanitizeName(req.datasetName() != null ? req.datasetName() : "dataset") + ".csv";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .header("Content-Disposition", "attachment; filename=\"" + name + "\"")
                    .body(bytes);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping(value = "/api/template/dataset.xlsx", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<byte[]> datasetXlsxTemplate(@ModelAttribute TemplateRequest req) {
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            var sheet = wb.createSheet("dataset");
            var headers = inferTemplateHeaders(req);
            var headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                var cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                sheet.autoSizeColumn(i);
            }
            var rows = buildScenarioRows(headers, req);
            for (int r = 0; r < rows.size(); r++) {
                var row = sheet.createRow(r + 1);
                var raw = rows.get(r);
                for (int c = 0; c < raw.size(); c++) {
                    var cell = row.createCell(c);
                    cell.setCellValue(raw.get(c));
                }
            }
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            wb.write(bos);
            byte[] bytes = bos.toByteArray();
            String name = sanitizeName(req.datasetName() != null ? req.datasetName() : "dataset") + ".xlsx";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header("Content-Disposition", "attachment; filename=\"" + name + "\"")
                    .body(bytes);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    private static java.util.List<String> inferTemplateHeaders(TemplateRequest req) {
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        String json = req.manualJson();
        if (json == null || json.isBlank()) {
            try {
                var call = CurlParser.parse(req.curl());
                json = call.getBody();
            } catch (Exception ignore) {}
        }
        if (json != null && !json.isBlank()) {
            try {
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                var node = mapper.readTree(json);
                if (node.isObject()) {
                    flattenJsonKeys((com.fasterxml.jackson.databind.node.ObjectNode) node, "", keys);
                } else {
                    keys.add("value");
                }
            } catch (Exception ex) {
                keys.add("value");
            }
        } else {
            keys.add("field1");
            keys.add("field2");
        }
        java.util.List<String> ordered = new java.util.ArrayList<>();
        // reserved metadata columns
        ordered.add("__case");
        ordered.add("__expected_status");
        ordered.add("__expected_json");
        ordered.add("__token_env");
        ordered.add("__auth");
        ordered.addAll(keys);
        return ordered;
    }

    private static java.util.List<String> buildPlaceholderValues(java.util.List<String> headers, TemplateRequest req) {
        java.util.List<String> vals = new java.util.ArrayList<>();
        com.fasterxml.jackson.databind.JsonNode root = null;
        String json = req.manualJson();
        if (json == null || json.isBlank()) {
            try { json = CurlParser.parse(req.curl()).getBody(); } catch (Exception ignore) {}
        }
        if (json != null && !json.isBlank()) {
            try { root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json); } catch (Exception ignore) {}
        }
        String sourceJson = root != null ? root.toString() : null;

        for (String h : headers) {
            if ("__case".equalsIgnoreCase(h)) { vals.add("positive"); continue; }
            if ("__expected_status".equalsIgnoreCase(h)) { vals.add("200"); continue; }
            if ("__expected_json".equalsIgnoreCase(h)) {
                vals.add(sourceJson != null && !sourceJson.isBlank() ? sourceJson : "{}");
                continue;
            }
            if ("__token_env".equalsIgnoreCase(h)) { vals.add(""); continue; }
            if ("__auth".equalsIgnoreCase(h)) { vals.add(""); continue; }
            String v = "";
            if (root != null) {
                com.fasterxml.jackson.databind.JsonNode n = lookupByPath(root, h);
                if (n != null) {
                    if (n.isBoolean()) v = "true";
                    else if (n.isIntegralNumber()) v = "1";
                    else if (n.isFloatingPointNumber()) v = "1.0";
                    else if (n.isArray()) v = "[]";
                    else if (n.isObject()) v = "{}";
                    else if (n.isTextual()) v = n.asText().isEmpty() ? "example" : n.asText();
                    else v = "";
                }
            }
            vals.add(v);
        }
        return vals;
    }

    private static java.util.List<java.util.List<String>> buildScenarioRows(java.util.List<String> headers, TemplateRequest req) {
        java.util.List<java.util.List<String>> rows = new java.util.ArrayList<>();
        java.util.List<String> base = buildPlaceholderValues(headers, req);
        int firstDataIdx = firstDataColumn(headers);
        int jsonIdx = indexOfIgnoreCase(headers, "__expected_json");

        String positiveJson = jsonIdx >= 0 ? base.get(jsonIdx) : null;
        if (positiveJson == null || positiveJson.isBlank()) positiveJson = "{}";
        String genericErrorJson = "{\"error\":{\"code\":\"invalid_request\"}}";
        String authErrorJson = "{\"error\":{\"code\":\"unauthorized\"}}";

        rows.add(applyScenario(base, headers, "positive", "200", null, positiveJson));
        rows.add(applyScenario(base, headers, "negative_missing", "400", firstDataIdx >= 0 ? "" : null, genericErrorJson));
        rows.add(applyScenario(base, headers, "negative_type", "400", firstDataIdx >= 0 ? "{{invalid_type}}" : null, genericErrorJson));
        rows.add(applyScenario(base, headers, "negative_null", "400", firstDataIdx >= 0 ? "null" : null, genericErrorJson));
        rows.add(applyScenario(base, headers, "auth", "401", null, authErrorJson));
        rows.add(applyScenario(base, headers, "idempotency", "200", null, positiveJson));
        rows.add(applyScenario(base, headers, "pagination", "200", firstDataIdx >= 0 ? "{{page_specific}}" : null, positiveJson));
        return rows;
    }

    private static java.util.List<String> applyScenario(java.util.List<String> base, java.util.List<String> headers, String caseName, String status, String firstFieldValue, String expectedJson) {
        java.util.List<String> row = new java.util.ArrayList<>(base);
        int caseIdx = indexOfIgnoreCase(headers, "__case");
        if (caseIdx >= 0) row.set(caseIdx, caseName);
        int statusIdx = indexOfIgnoreCase(headers, "__expected_status");
        if (statusIdx >= 0) row.set(statusIdx, status);
        int jsonIdx = indexOfIgnoreCase(headers, "__expected_json");
        if (jsonIdx >= 0 && expectedJson != null) row.set(jsonIdx, expectedJson);
        int tokenIdx = indexOfIgnoreCase(headers, "__token_env");
        int authIdx = indexOfIgnoreCase(headers, "__auth");
        if ("auth".equalsIgnoreCase(caseName)) {
            if (authIdx >= 0) row.set(authIdx, "none");
            if (tokenIdx >= 0) row.set(tokenIdx, "");
        } else {
            if (authIdx >= 0) row.set(authIdx, "");
            if (tokenIdx >= 0 && row.get(tokenIdx) == null) row.set(tokenIdx, "");
        }
        int dataIdx = firstDataColumn(headers);
        if (firstFieldValue != null && dataIdx >= 0) {
            row.set(dataIdx, firstFieldValue);
        }
        return row;
    }

    private static int firstDataColumn(java.util.List<String> headers) {
        for (int i = 0; i < headers.size(); i++) {
            String h = headers.get(i);
            if ("__case".equalsIgnoreCase(h)
                    || "__expected_status".equalsIgnoreCase(h)
                    || "__expected_json".equalsIgnoreCase(h)
                    || "__token_env".equalsIgnoreCase(h)
                    || "__auth".equalsIgnoreCase(h)) continue;
            return i;
        }
        return -1;
    }

    private static int indexOfIgnoreCase(java.util.List<String> headers, String key) {
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i) != null && headers.get(i).equalsIgnoreCase(key)) return i;
        }
        return -1;
    }

    private static String escapeCsvVal(String s) {
        if (s == null) return "";
        boolean need = s.contains(",") || s.contains("\n") || s.contains("\"");
        String out = s.replace("\"", "\"\"");
        return need ? ("\"" + out + "\"") : out;
    }

    private static void flattenJsonKeys(com.fasterxml.jackson.databind.node.ObjectNode node, String prefix, java.util.Set<String> out) {
        java.util.Iterator<String> it = node.fieldNames();
        while (it.hasNext()) {
            String f = it.next();
            com.fasterxml.jackson.databind.JsonNode child = node.get(f);
            String path = prefix.isEmpty() ? f : (prefix + "." + f);
            if (child != null && child.isObject()) {
                flattenJsonKeys((com.fasterxml.jackson.databind.node.ObjectNode) child, path, out);
            } else {
                out.add(path);
            }
        }
    }

    private static com.fasterxml.jackson.databind.JsonNode lookupByPath(com.fasterxml.jackson.databind.JsonNode root, String path) {
        String[] parts = path.split("\\.");
        com.fasterxml.jackson.databind.JsonNode cur = root;
        for (String p : parts) {
            if (cur == null) return null;
            cur = cur.get(p);
        }
        return cur;
    }

    // ----- UI-friendly HTML endpoints -----

    @PostMapping(value = "/ui/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> uiGenerateMultipart(
            @RequestParam("curl") String curl,
            @RequestParam(value = "notes", required = false) String notes,
            @RequestParam(value = "tokenEnvVar", required = false) String tokenEnvVar,
            @RequestParam(value = "outDir", required = false) String outDir,
            @RequestParam(value = "extraHeaders", required = false) String extraHeaders,
            @RequestParam(value = "queryParams", required = false) String queryParams,
            @RequestParam(value = "successStatus", required = false) Integer successStatus,
            @RequestParam(value = "datasetName", required = false) String datasetName,
            @RequestParam(value = "manualJson", required = false) String manualJson,
            @RequestParam(value = "chain", required = false) String chain,
            @RequestParam(value = "includeNegatives", required = false) Boolean includeNegatives,
            @RequestParam(value = "includeTypeNegatives", required = false) Boolean includeTypeNegatives,
            @RequestParam(value = "includeNullNegatives", required = false) Boolean includeNullNegatives,
            @RequestParam(value = "includeIdempotency", required = false) Boolean includeIdempotency,
            @RequestParam(value = "includePagination", required = false) Boolean includePagination,
            @RequestParam(value = "featureName", required = false) String featureName,
            @RequestParam(value = "assertions", required = false) String assertions,
            @RequestParam(value = "expStatusPositive", required = false) Integer expStatusPositive,
            @RequestParam(value = "expStatusIdem", required = false) Integer expStatusIdem,
            @RequestParam(value = "expStatusAuth", required = false) Integer expStatusAuth,
            @RequestParam(value = "expStatusNegative", required = false) Integer expStatusNegative,
            @RequestParam(value = "expJsonPositive", required = false) String expJsonPositive,
            @RequestParam(value = "expJsonIdem", required = false) String expJsonIdem,
            @RequestParam(value = "expJsonAuth", required = false) String expJsonAuth,
            @RequestParam(value = "expJsonNegative", required = false) String expJsonNegative,
            @RequestParam(value = "requiredFields", required = false) String requiredFields,
            @RequestParam(value = "lengthRules", required = false) String lengthRules,
            @RequestPart(value = "dataset", required = false) MultipartFile dataset
    ) {
        GenerateRequest req = new GenerateRequest(curl, notes, tokenEnvVar, outDir, extraHeaders, queryParams, successStatus, datasetName, manualJson, chain,
                includeNegatives, includeTypeNegatives, includeNullNegatives, includeIdempotency, includePagination, featureName, assertions,
                expStatusPositive, expStatusIdem, expStatusAuth, expStatusNegative,
                expJsonPositive, expJsonIdem, expJsonAuth, expJsonNegative,
                requiredFields,
                lengthRules);
        ResponseEntity<?> result = handle(req, dataset);
        return renderHtml(result);
    }

    @PostMapping(value = "/ui/generate", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> uiGenerateForm(@ModelAttribute GenerateRequest req) {
        ResponseEntity<?> result = handle(req, null);
        return renderHtml(result);
    }

    @PostMapping(value = "/ui/generate", consumes = MediaType.ALL_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> uiGenerateAny(
            @RequestParam(value = "curl", required = false) String curl,
            @RequestParam(value = "notes", required = false) String notes,
            @RequestParam(value = "tokenEnvVar", required = false) String tokenEnvVar,
            @RequestParam(value = "outDir", required = false) String outDir,
            @RequestParam(value = "extraHeaders", required = false) String extraHeaders,
            @RequestParam(value = "queryParams", required = false) String queryParams,
            @RequestParam(value = "successStatus", required = false) Integer successStatus,
            @RequestParam(value = "datasetName", required = false) String datasetName,
            @RequestParam(value = "manualJson", required = false) String manualJson,
            @RequestParam(value = "chain", required = false) String chain,
            @RequestParam(value = "includeNegatives", required = false) Boolean includeNegatives,
            @RequestParam(value = "includeTypeNegatives", required = false) Boolean includeTypeNegatives,
            @RequestParam(value = "includeNullNegatives", required = false) Boolean includeNullNegatives,
            @RequestParam(value = "includeIdempotency", required = false) Boolean includeIdempotency,
            @RequestParam(value = "includePagination", required = false) Boolean includePagination,
            @RequestParam(value = "featureName", required = false) String featureName,
            @RequestParam(value = "assertions", required = false) String assertions,
            @RequestParam(value = "expStatusPositive", required = false) Integer expStatusPositive,
            @RequestParam(value = "expStatusIdem", required = false) Integer expStatusIdem,
            @RequestParam(value = "expStatusAuth", required = false) Integer expStatusAuth,
            @RequestParam(value = "expStatusNegative", required = false) Integer expStatusNegative,
            @RequestParam(value = "expJsonPositive", required = false) String expJsonPositive,
            @RequestParam(value = "expJsonIdem", required = false) String expJsonIdem,
            @RequestParam(value = "expJsonAuth", required = false) String expJsonAuth,
            @RequestParam(value = "expJsonNegative", required = false) String expJsonNegative,
            @RequestParam(value = "requiredFields", required = false) String requiredFields,
            @RequestParam(value = "lengthRules", required = false) String lengthRules,
            @RequestParam(value = "dataset", required = false) MultipartFile dataset
    ) {
        if (curl == null || curl.isBlank()) {
            return ResponseEntity.status(415).body("<pre>Unsupported Content-Type or missing 'curl'</pre>");
        }
        GenerateRequest req = new GenerateRequest(curl, notes, tokenEnvVar, outDir, extraHeaders, queryParams, successStatus, datasetName, manualJson, chain,
                includeNegatives, includeTypeNegatives, includeNullNegatives, includeIdempotency, includePagination, featureName, assertions,
                expStatusPositive, expStatusIdem, expStatusAuth, expStatusNegative,
                expJsonPositive, expJsonIdem, expJsonAuth, expJsonNegative,
                requiredFields,
                lengthRules);
        ResponseEntity<?> result = handle(req, dataset);
        return renderHtml(result);
    }

    private ResponseEntity<String> renderHtml(ResponseEntity<?> result) {
        int status = result.getStatusCode().value();
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html><head><meta charset='utf-8'><title>Generated Feature</title>")
                .append("<style>body{font-family:system-ui,Segoe UI,Roboto,Helvetica,Arial;margin:24px} pre{background:#f6f8fa;padding:12px;white-space:pre-wrap} .ok{color:#137333}.err{color:#c62828} code{background:#f0f0f0;padding:2px 4px;border-radius:3px}</style>")
                .append("</head><body>");
        Map<?,?> map = (result.getBody() instanceof Map) ? (Map<?,?>) result.getBody() : null;
        if (map != null) {
            Object ok = map.get("ok");
            boolean success = ok instanceof Boolean b && b;
            if (success) {
                html.append("<div class='ok'>✅ Generated</div>");
                appendIf(map, html, "path", "Feature file");
                appendIf(map, html, "datasetPath", "Dataset");
                appendIf(map, html, "datasetRows", "Rows");
                Object fp = map.get("featurePreview");
                if (fp != null) {
                    html.append("<h3>Feature Preview</h3><pre>").append(escapeHtml(fp.toString())).append("</pre>");
                }
                Object as = map.get("assertionsApplied");
                if (as instanceof java.util.Collection<?> c && !c.isEmpty()) {
                    html.append("<h4>Assertions</h4><ul>");
                    for (Object o : c) html.append("<li>").append(escapeHtml(String.valueOf(o))).append("</li>");
                    html.append("</ul>");
                }
                Object eo = map.get("expectedOverrides");
                if (eo instanceof java.util.Map<?,?> m && !m.isEmpty()) {
                    html.append("<h4>Expected Overrides</h4><ul>");
                    for (var e : m.entrySet()) {
                        String k = String.valueOf(e.getKey());
                        String v = String.valueOf(e.getValue());
                        html.append("<li><b>").append(escapeHtml(k)).append(":</b> ");
                        if (k.endsWith("Json")) {
                            html.append("<pre>").append(escapeHtml(v)).append("</pre>");
                        } else {
                            html.append("<code>").append(escapeHtml(v)).append("</code>");
                        }
                        html.append("</li>");
                    }
                    html.append("</ul>");
                }
                Object reqf = map.get("requiredFieldsApplied");
                if (reqf instanceof java.util.Collection<?> rc && !rc.isEmpty()) {
                    html.append("<h4>Required Fields (negative)</h4><ul>");
                    for (Object o : rc) html.append("<li><code>").append(escapeHtml(String.valueOf(o))).append("</code></li>");
                    html.append("</ul>");
                }
                Object lra = map.get("lengthRulesApplied");
                if (lra instanceof java.util.Collection<?> lc && !lc.isEmpty()) {
                    html.append("<h4>Length Rules (negative)</h4><ul>");
                    for (Object o : lc) html.append("<li><code>").append(escapeHtml(String.valueOf(o))).append("</code></li>");
                    html.append("</ul>");
                }
                appendListIf(map, html, "datasetMissingHeaders", "Missing headers");
                appendListIf(map, html, "datasetExtraHeaders", "Extra headers");
                Object mmObj = map.get("datasetTypeMismatches");
                if (mmObj instanceof java.util.List<?> mm && !mm.isEmpty()) {
                    html.append("<h4>Type mismatches</h4><ul>");
                    for (Object o : mm) html.append("<li>").append(escapeHtml(String.valueOf(o))).append("</li>");
                    html.append("</ul>");
                }
            } else {
                Object err = map.containsKey("error") ? map.get("error") : "Generation failed";
                html.append("<div class='err'>❌ ").append(escapeHtml(String.valueOf(err))).append("</div>");
            }
        } else {
            html.append("<div class='err'>❌ Unexpected response</div>");
        }
        html.append("<p><a href='/'>&larr; Back</a></p>");
        html.append("</body></html>");
        return ResponseEntity.status(status).contentType(MediaType.TEXT_HTML).body(html.toString());
    }

    private static java.util.List<String> mergeAssertions(java.util.List<String> baseline, java.util.List<String> custom) {
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>();
        if (baseline != null) merged.addAll(baseline);
        if (custom != null) merged.addAll(custom);
        return new java.util.ArrayList<>(merged);
    }

    private static java.util.List<String> combineScenarioAssertions(java.util.List<String> headerAssertions,
                                                                    java.util.List<String> customAssertions,
                                                                    String sampleJson,
                                                                    boolean includeCustom) {
        java.util.List<String> jsonAssertions = buildJsonAssertions(sampleJson);
        java.util.List<String> merged = mergeAssertions(headerAssertions, jsonAssertions);
        if (includeCustom) {
            merged = mergeAssertions(merged, customAssertions);
        }
        return merged;
    }

    private static java.util.List<String> buildHeaderAssertions(ApiCall call) {
        java.util.LinkedHashSet<String> steps = new java.util.LinkedHashSet<>();
        String contentType = headerValue(call, "Content-Type");
        if (contentType == null) contentType = headerValue(call, "Accept");
        if (contentType != null && contentType.toLowerCase().contains("application/json")) {
            steps.add("Then the response header \"Content-Type\" should equal \"application/json\"");
        }
        return new java.util.ArrayList<>(steps);
    }

    private static java.util.List<String> buildJsonAssertions(String sampleJson) {
        if (sampleJson == null || sampleJson.isBlank()) return java.util.List.of();
        java.util.LinkedHashSet<String> steps = new java.util.LinkedHashSet<>();
        if (sampleJson != null && !sampleJson.isBlank()) {
            try {
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                var node = mapper.readTree(sampleJson);
                collectBaselineJsonAssertions(node, "$", steps);
            } catch (Exception ignore) {}
        }
        return new java.util.ArrayList<>(steps);
    }

    private static String headerValue(ApiCall call, String key) {
        for (var entry : call.getHeaders().entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static void collectBaselineJsonAssertions(com.fasterxml.jackson.databind.JsonNode node, String path, java.util.Set<String> steps) {
        if (node == null || node.isNull()) {
            steps.add("Then the response json path \"" + path + "\" should exist");
            return;
        }
        if (node.isObject()) {
            java.util.Iterator<java.util.Map.Entry<String, com.fasterxml.jackson.databind.JsonNode>> it = node.fields();
            while (it.hasNext()) {
                var entry = it.next();
                String childPath = "$".equals(path) ? path + "." + entry.getKey() : path + "." + entry.getKey();
                collectBaselineJsonAssertions(entry.getValue(), childPath, steps);
            }
            return;
        }
        if (node.isArray()) {
            steps.add("Then the response json path \"" + path + "\" should exist");
            if (node.size() > 0) {
                collectBaselineJsonAssertions(node.get(0), path + "[0]", steps);
            }
            return;
        }
        if (node.isTextual()) {
            String val = node.asText();
            if (!val.isBlank() && val.length() <= 36 && !val.contains("\n")) {
                steps.add("Then the response json path \"" + path + "\" should equal \"" + escapeValue(val) + "\"");
            } else {
                steps.add("Then the response json path \"" + path + "\" should exist");
            }
            return;
        }
        if (node.isNumber() || node.isBoolean()) {
            steps.add("Then the response json path \"" + path + "\" should equal \"" + escapeValue(node.asText()) + "\"");
            return;
        }
        steps.add("Then the response json path \"" + path + "\" should exist");
    }

    private static String escapeValue(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static java.util.Set<String> filterReservedHeaders(java.util.List<String> rawHeaders){
        java.util.LinkedHashSet<String> filtered = new java.util.LinkedHashSet<>();
        if (rawHeaders == null) return filtered;
        java.util.Set<String> reserved = java.util.Set.of(
                "__case", "__expected_status", "__expected_json", "__token_env", "__auth",
                "__headers", "__path", "__base", "__base_url"
        );
        for (String h : rawHeaders){
            if (h == null) continue;
            String trimmed = h.trim();
            if (trimmed.isEmpty()) continue;
            String normalized = trimmed.toLowerCase();
            if (reserved.contains(normalized)) continue;
            filtered.add(trimmed);
        }
        return filtered;
    }

    private static void appendIf(Map<?,?> map, StringBuilder html, String key, String label) {
        Object v = map.get(key);
        if (v != null) html.append("<div><b>").append(label).append(":</b> <code>").append(escapeHtml(String.valueOf(v))).append("</code></div>");
    }
    private static void appendListIf(Map<?,?> map, StringBuilder html, String key, String label) {
        Object v = map.get(key);
        if (v instanceof java.util.Collection<?> c && !c.isEmpty()) {
            html.append("<h4>").append(label).append("</h4><ul>");
            for (Object o : c) html.append("<li>").append(escapeHtml(String.valueOf(o))).append("</li>");
            html.append("</ul>");
        }
    }
    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static java.util.List<String> parseRequiredPaths(String raw){
        java.util.List<String> list = new java.util.ArrayList<>();
        if (raw == null) return list;
        for (String line : raw.split("\n")){
            if (line == null) continue;
            String t = line.trim();
            if (t.isEmpty()) continue;
            list.add(t);
        }
        return list;
    }

    // Parse length rules from textarea format: one per line => path|min|max
    // Empty min/max are allowed and will be returned as nulls in the tuple
    private static java.util.List<String[]> parseLengthRules(String raw){
        java.util.List<String[]> list = new java.util.ArrayList<>();
        if (raw == null || raw.isBlank()) return list;
        for (String line : raw.split("\n")){
            if (line == null) continue;
            String t = line.trim();
            if (t.isEmpty()) continue;
            String[] parts = t.split("\\|", -1);
            if (parts.length == 0) continue;
            String path = parts[0] == null ? "" : parts[0].trim();
            if (path.isEmpty()) continue;
            String min = parts.length > 1 ? parts[1].trim() : null;
            String max = parts.length > 2 ? parts[2].trim() : null;
            if (min != null && min.isEmpty()) min = null;
            if (max != null && max.isEmpty()) max = null;
            list.add(new String[]{path, min, max});
        }
        return list;
    }

    // Apply a target length to a string or array at the given dotted path (supports [idx] array tokens)
    // Returns true if a supported node was found and mutated.
    private static boolean applyLength(com.fasterxml.jackson.databind.JsonNode root, String dottedPath, int targetLen){
        if (root == null || dottedPath == null || dottedPath.isBlank()) return false;
        java.util.List<String> tokens = java.util.Arrays.asList(dottedPath.split("\\."));
        com.fasterxml.jackson.databind.JsonNode cur = root;
        for (int i = 0; i < tokens.size()-1; i++){
            String token = tokens.get(i);
            NameIdx ni = parseNameIdx(token);
            if (!cur.isObject()) return false;
            cur = cur.get(ni.name);
            if (cur == null) return false;
            if (ni.idx != null) {
                if (!cur.isArray()) return false;
                cur = cur.get(ni.idx);
                if (cur == null) return false;
            }
        }
        String lastToken = tokens.get(tokens.size()-1);
        NameIdx last = parseNameIdx(lastToken);
        if (!cur.isObject()) return false;
        com.fasterxml.jackson.databind.node.ObjectNode parent = (com.fasterxml.jackson.databind.node.ObjectNode) cur;
        com.fasterxml.jackson.databind.JsonNode child = parent.get(last.name);
        if (child == null) return false;
        if (last.idx != null){
            if (!child.isArray()) return false;
            com.fasterxml.jackson.databind.node.ArrayNode arr = (com.fasterxml.jackson.databind.node.ArrayNode) child;
            com.fasterxml.jackson.databind.JsonNode elem = arr.get(last.idx);
            if (elem == null) return false;
            if (elem.isTextual()) {
                String s = elem.asText();
                String base = s != null && !s.isEmpty() ? s.substring(0,1) : "x";
                StringBuilder sb = new StringBuilder();
                for (int i=0;i<targetLen;i++) sb.append(base);
                arr.set(last.idx, com.fasterxml.jackson.databind.node.TextNode.valueOf(sb.toString()));
                return true;
            } else if (elem.isArray()) {
                return resizeArray((com.fasterxml.jackson.databind.node.ArrayNode) elem, targetLen);
            } else {
                return false;
            }
        } else {
            if (child.isTextual()) {
                String s = child.asText();
                String base = s != null && !s.isEmpty() ? s.substring(0,1) : "x";
                StringBuilder sb = new StringBuilder();
                for (int i=0;i<targetLen;i++) sb.append(base);
                parent.put(last.name, sb.toString());
                return true;
            } else if (child.isArray()) {
                return resizeArray((com.fasterxml.jackson.databind.node.ArrayNode) child, targetLen);
            } else {
                return false;
            }
        }
    }

    private static boolean resizeArray(com.fasterxml.jackson.databind.node.ArrayNode arr, int targetLen){
        int curLen = arr.size();
        if (targetLen < 0) targetLen = 0;
        if (targetLen == curLen) return true;
        if (targetLen < curLen){
            // truncate
            for (int i = curLen - 1; i >= targetLen; i--) arr.remove(i);
            return true;
        } else {
            // grow: clone last element if present; otherwise add {}
            com.fasterxml.jackson.databind.JsonNode cloneSrc = curLen > 0 ? arr.get(curLen - 1) : null;
            for (int i = curLen; i < targetLen; i++){
                if (cloneSrc != null) arr.add(cloneSrc.deepCopy());
                else arr.add(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode());
            }
            return true;
        }
    }

    private record NameIdx(String name, Integer idx) {}
    private static NameIdx parseNameIdx(String token){
        if (token == null) return new NameIdx("", null);
        int b = token.indexOf('[');
        int e = token.indexOf(']', b+1);
        if (b >= 0 && e > b){
            String name = token.substring(0,b);
            Integer idx = null;
            try { idx = Integer.parseInt(token.substring(b+1, e)); } catch (Exception ignore) {}
            return new NameIdx(name, idx);
        }
        return new NameIdx(token, null);
    }

    // Assertions encoding: one per line, format
    // JSONPATH_EQUALS|$.path|value
    // JSONPATH_CONTAINS|$.path|value
    // JSONPATH_EXISTS|$.path|
    // HEADER_EQUALS|Header-Name|value
    private static java.util.List<String> buildAssertionSteps(String raw) {
        java.util.List<String> steps = new java.util.ArrayList<>();
        if (raw == null || raw.isBlank()) return steps;
        for (String line : raw.split("\n")) {
            if (line == null) continue;
            String t = line.trim();
            if (t.isEmpty()) continue;
            String[] parts = t.split("\\|", -1);
            if (parts.length < 2) continue;
            String type = parts[0].trim().toUpperCase();
            String a = parts.length > 1 ? parts[1] : "";
            String b = parts.length > 2 ? parts[2] : "";
            a = a.replace("\"", "\\\"");
            b = b.replace("\"", "\\\"");
            switch (type) {
                case "JSONPATH_EQUALS" -> steps.add("Then the response json path \"" + a + "\" should equal \"" + b + "\"");
                case "JSONPATH_CONTAINS" -> steps.add("Then the response json path \"" + a + "\" should contain \"" + b + "\"");
                case "JSONPATH_EXISTS" -> steps.add("Then the response json path \"" + a + "\" should exist");
                case "REMEMBER_JSONPATH" -> steps.add("Then remember json path \"" + a + "\" as \"" + b + "\"");
                case "HEADER_EQUALS" -> steps.add("Then the response header \"" + a + "\" should equal \"" + b + "\"");
                case "HEADER_EXISTS" -> steps.add("Then the response header \"" + a + "\" should exist");
                case "BODY_CONTAINS" -> steps.add("Then the response body should contain \"" + b + "\"");
                case "BODY_REGEX" -> steps.add("Then the response body should match regex \"" + b + "\"");
                case "JSON_EQUALS" -> steps.add("Then the response json should equal \"" + b + "\"");
                case "SCHEMA" -> {
                    String path = (a == null || a.isBlank()) ? b : a;
                    if (path != null && !path.isBlank()) {
                        steps.add("Then the response should match schema \"" + path + "\"");
                    }
                }
                case "JSONPATH_LENGTH_EQ" -> steps.add("Then the response json path \"" + a + "\" length should be " + safeInt(b, 0));
                case "JSONPATH_LENGTH_GTE" -> steps.add("Then the response json path \"" + a + "\" length should be at least " + safeInt(b, 0));
                case "JSONPATH_LENGTH_LTE" -> steps.add("Then the response json path \"" + a + "\" length should be at most " + safeInt(b, 0));
                case "JSONPATH_NUMBER_GTE" -> steps.add("Then the response json path \"" + a + "\" should be >= " + safeDouble(b, 0));
                case "JSONPATH_NUMBER_LTE" -> steps.add("Then the response json path \"" + a + "\" should be <= " + safeDouble(b, 0));
                case "JSONPATH_NUMBER_EQ" -> steps.add("Then the response json path \"" + a + "\" should equal \"" + b + "\"");
                case "JSONPATH_NUMBER_BETWEEN" -> {
                    String[] parts2 = b.split(",");
                    double min = parts2.length>0 ? safeDouble(parts2[0], 0) : 0;
                    double max = parts2.length>1 ? safeDouble(parts2[1], 0) : min;
                    steps.add("Then the response json path \"" + a + "\" should be between " + min + " and " + max);
                }
                case "JSONPATH_IS_NUMBER" -> steps.add("Then the response json path \"" + a + "\" should be a number");
                case "JSONPATH_IS_INTEGER" -> steps.add("Then the response json path \"" + a + "\" should be an integer");
            }
        }
        return steps;
    }

    private static int safeInt(String s, int def){
        try { return Integer.parseInt(String.valueOf(s).trim()); } catch (Exception e) { return def; }
    }
    private static double safeDouble(String s, double def){
        try { return Double.parseDouble(String.valueOf(s).trim()); } catch (Exception e) { return def; }
    }

    private void triggerAutoPush() {
        try {
            java.nio.file.Path script = java.nio.file.Paths.get("scripts", "push-generated.sh");
            if (!java.nio.file.Files.exists(script)) {
                return;
            }
            ProcessBuilder pb = new ProcessBuilder("bash", "-lc", script.toAbsolutePath().toString());
            pb.redirectErrorStream(true);
            pb.start();
        } catch (IOException ex) {
            log.warn("Failed to trigger git auto-push: {}", ex.getMessage());
        }
    }


}
