package com.example.generator;

import com.example.curl.ApiCall;
import com.example.rules.SimpleRules;

import java.net.URI;
import java.util.List;
import java.util.Set;

public class FeatureGenerator {
    public static String slugify(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9]+","_").replaceAll("^_+|_+$", "");
    }

    public static String pathOf(String url) {
        return URI.create(url).getPath();
    }

    public static String generateFeature(ApiCall call) {
        return generateFeature(call, "API_TOKEN");
    }

    public static String generateFeature(ApiCall call, String tokenEnvVar) {
        java.util.List<String[]> emptyPairs = java.util.Collections.emptyList();
        java.util.List<String> emptyStrings = java.util.Collections.emptyList();
        return generateFeature(call, tokenEnvVar, emptyPairs, emptyPairs, 200, null, 0, true, true, true,
                emptyStrings, emptyStrings, emptyStrings, emptyStrings,
                null, null, null, null, null, null, null, null, null, true, true, java.util.Set.of());
    }

    public static String generateFeature(ApiCall call, String tokenEnvVar, java.util.List<String[]> extraHeaders, java.util.List<String[]> queryParams, Integer successStatus) {
        java.util.List<String> emptyStrings = java.util.Collections.emptyList();
        return generateFeature(call, tokenEnvVar, extraHeaders, queryParams, successStatus, null, 0, true, true, true,
                emptyStrings, emptyStrings, emptyStrings, emptyStrings,
                null, null, null, null, null, null, null, null, null, true, true, java.util.Set.of());
    }

    public static String generateFeature(
            ApiCall call,
            String tokenEnvVar,
            java.util.List<String[]> extraHeaders,
            java.util.List<String[]> queryParams,
            Integer successStatus,
            String datasetCsvPath,
            int datasetRowCount,
            boolean includeNegatives,
            boolean includeIdempotency,
            boolean includePagination,
            java.util.List<String> assertionsPositive,
            java.util.List<String> assertionsIdem,
            java.util.List<String> assertionsAuth,
            java.util.List<String> assertionsNegative,
            Integer expStatusPositive,
            Integer expStatusIdem,
            Integer expStatusAuth,
            Integer expStatusNegative,
            String expJsonPositive,
            String expJsonIdem,
            String expJsonAuth,
            String expJsonNegative,
            java.util.List<String> requiredNegativePaths,
            boolean includeTypeNegatives,
            boolean includeNullNegatives,
            java.util.Set<String> datasetHeaders
    ) {
        boolean hasExpectedJsonColumn = datasetHeaders != null && datasetHeaders.stream().anyMatch(h -> "__expected_json".equalsIgnoreCase(h));
        String path = pathOf(call.getUrl());
        // Append provided query params to path if any
        if (queryParams != null && !queryParams.isEmpty()) {
            StringBuilder qs = new StringBuilder();
            for (int i=0;i<queryParams.size();i++){
                String[] kv = queryParams.get(i);
                if (kv == null || kv.length == 0) continue;
                String k = kv[0] == null ? "" : kv[0];
                String v = kv.length > 1 && kv[1] != null ? kv[1] : "";
                if (qs.length() == 0) qs.append("?"); else qs.append("&");
                qs.append(java.net.URLEncoder.encode(k, java.nio.charset.StandardCharsets.UTF_8));
                if (!v.isEmpty()) {
                    qs.append("=").append(java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            path = path + qs;
        }
        String resource = slugify(path.replaceAll("/+$",""));
        if (resource.isBlank()) resource = "root";
        String verb = call.getMethod().name().toLowerCase();
        String authBlock = "";
        if ("Bearer".equalsIgnoreCase(call.getAuthType())) {
            String token = (tokenEnvVar == null || tokenEnvVar.isBlank()) ? "API_TOKEN" : tokenEnvVar;
            authBlock = "    And I use bearer token from env \"" + token + "\"\n";
        }

        String bodyStep;
        if (call.getBody() != null && !call.getBody().isBlank()) {
            bodyStep = "    When I " + call.getMethod().name() + " to \"" + path + "\" with json body:\n      \"\"\"\n" + call.getBody() + "\n      \"\"\"\n";
        } else {
            bodyStep = "    When I " + call.getMethod().name() + " to \"" + path + "\"\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Feature: ").append(call.getMethod().name()).append(" ").append(path).append("\n")
          .append("  Background:\n")
          .append("    Given the API base url is \"${BASE_URL}\"\n")
          .append(authBlock);

        if (extraHeaders != null) {
            for (String[] hv : extraHeaders) {
                if (hv != null && hv.length >= 2) {
                    String name = hv[0] == null ? "" : hv[0];
                    String value = hv[1] == null ? "" : hv[1];
                    sb.append("    And I add header \"")
                      .append(escapeGherkin(name))
                      .append("\" with value ")
                      .append(quoteGherkinValue(value))
                      .append("\n");
                }
            }
        }

        if (datasetCsvPath != null && (call.getMethod() == ApiCall.Method.POST || call.getMethod() == ApiCall.Method.PUT || call.getMethod() == ApiCall.Method.PATCH)) {
            java.util.Map<String, java.util.List<Integer>> groups = groupRowsByCase(datasetCsvPath);
            if (groups.isEmpty()) {
                // fallback single outline with given row count if grouping failed
                sb.append("\n  @positive\n  Scenario Outline: Happy path with dataset\n");
                sb.append("    When I ").append(call.getMethod().name()).append(" to \"").append(path).append("\" with json body from csv \"")
                  .append(datasetCsvPath).append("\" row <row>\n");
                sb.append("    Then the response status should be from csv column \"__expected_status\" row <row>\n");
                if (hasExpectedJsonColumn) {
                    sb.append("    Then the response json should equal from csv column \"__expected_json\" row <row>\n");
                }
                sb.append(appendedAssertions(assertionsPositive));
                if (!hasExpectedJsonColumn) {
                    sb.append(appendedDocJson(expJsonPositive));
                }
                sb.append("\n    Examples:\n");
                sb.append("      | row |\n");
                for (int i = 1; i <= Math.max(0, datasetRowCount); i++) {
                    sb.append("      | ").append(i).append("   |\n");
                }
            } else {
                for (var e : groups.entrySet()) {
                    String caseName = e.getKey();
                    java.util.List<Integer> rows = e.getValue();
                    String tag = mapCaseToTag(caseName);
                    sb.append("\n  ").append(tag).append("\n  Scenario Outline: ")
                      .append(capitalize(caseName)).append(" cases from dataset\n");
                    sb.append("    When I ").append(call.getMethod().name()).append(" to \"").append(path).append("\" with json body from csv \"")
                      .append(datasetCsvPath).append("\" row <row>\n");
                    sb.append("    Then the response status should be from csv column \"__expected_status\" row <row>\n");
                    if (hasExpectedJsonColumn) {
                        sb.append("    Then the response json should equal from csv column \"__expected_json\" row <row>\n");
                    }
                    java.util.List<String> caseAssertions = assertionsForCase(caseName, assertionsPositive, assertionsIdem, assertionsAuth, assertionsNegative);
                    sb.append(appendedAssertions(caseAssertions));
                    if (!hasExpectedJsonColumn) {
                        sb.append(appendedDocJson(expJsonPositive));
                    }
                    sb.append("\n    Examples:\n");
                    sb.append("      | row |\n");
                    for (Integer r : rows) {
                        sb.append("      | ").append(r).append("   |\n");
                    }
                }
            }
        } else {
            int posStatus = expStatusPositive != null ? expStatusPositive : (successStatus == null ? 200 : successStatus);
            sb.append("\n  @happy\n  Scenario: Happy path generated from cURL\n")
              .append(bodyStep)
              .append("    Then the response status should be ").append(posStatus).append("\n")
              .append(appendedAssertions(assertionsPositive))
              .append(appendedDocJson(expJsonPositive));
        }

        boolean datasetProvided = datasetCsvPath != null;

        // Auth negative (skip when dataset is provided; dataset should drive all cases)
        if (!datasetProvided && authBlock.contains("bearer token")) {
            int authStatus = expStatusAuth != null ? expStatusAuth : 401;
            sb.append("\n  @auth\n  Scenario: Missing token returns 401\n")
              .append("    Given I remove authorization header\n")
              .append(bodyStep)
              .append("    Then the response status should be ").append(authStatus).append("\n")
              .append(appendedAssertions(assertionsAuth))
              .append(appendedDocJson(expJsonAuth));
        }

        // Negative cases: missing required fields (top-level) if body present
        if (!datasetProvided && includeNegatives) {
            int negStatus = expStatusNegative != null ? expStatusNegative : 400;
            List<SimpleRules.NegativeCase> negs = SimpleRules.deriveMissingFieldCases(call.getBody(), negStatus, requiredNegativePaths);
            for (SimpleRules.NegativeCase nc : negs) {
                sb.append("\n  @negative\n  Scenario: ").append(nc.name).append("\n")
                  .append("    When I ").append(call.getMethod().name()).append(" to \"").append(path).append("\" with json body:\n")
                  .append("      \"\"\"\n").append(nc.jsonBody).append("\n      \"\"\"\n")
                  .append("    Then the response status should be ").append(nc.expectedStatus).append("\n")
                  .append(appendedAssertions(assertionsNegative))
                  .append(appendedDocJson(expJsonNegative));
            }
        }

        if (!datasetProvided && includeNegatives && includeTypeNegatives) {
            int negStatus = expStatusNegative != null ? expStatusNegative : 400;
            List<SimpleRules.NegativeCase> negs = SimpleRules.deriveTypeMismatchCases(call.getBody(), negStatus);
            for (SimpleRules.NegativeCase nc : negs) {
                sb.append("\n  @negative @datatype\n  Scenario: ").append(nc.name).append("\n")
                  .append("    When I ").append(call.getMethod().name()).append(" to \"").append(path).append("\" with json body:\n")
                  .append("      \"\"\"\n").append(nc.jsonBody).append("\n      \"\"\"\n")
                  .append("    Then the response status should be ").append(nc.expectedStatus).append("\n")
                  .append(appendedAssertions(assertionsNegative))
                  .append(appendedDocJson(expJsonNegative));
            }
        }

        if (!datasetProvided && includeNegatives && includeNullNegatives) {
            int negStatus = expStatusNegative != null ? expStatusNegative : 400;
            List<SimpleRules.NegativeCase> negs = SimpleRules.deriveNullInjectionCases(call.getBody(), negStatus);
            for (SimpleRules.NegativeCase nc : negs) {
                sb.append("\n  @negative @null\n  Scenario: ").append(nc.name).append("\n")
                  .append("    When I ").append(call.getMethod().name()).append(" to \"").append(path).append("\" with json body:\n")
                  .append("      \"\"\"\n").append(nc.jsonBody).append("\n      \"\"\"\n")
                  .append("    Then the response status should be ").append(nc.expectedStatus).append("\n")
                  .append(appendedAssertions(assertionsNegative))
                  .append(appendedDocJson(expJsonNegative));
            }
        }

        // Idempotency: if header present or method is PUT/DELETE
        boolean hasIdem = call.getHeaders().keySet().stream().anyMatch(h -> h.equalsIgnoreCase("Idempotency-Key") || h.equalsIgnoreCase("X-Idempotency-Key"));
        if (!datasetProvided && includeIdempotency && (hasIdem || call.getMethod() == ApiCall.Method.PUT || call.getMethod() == ApiCall.Method.DELETE)) {
            int idemStatus = expStatusIdem != null ? expStatusIdem : 200;
            sb.append("\n  @idempotency\n  Scenario: Repeating the same request is idempotent\n")
              .append(bodyStep)
              .append("    Then the response status should be ").append(idemStatus).append("\n")
              .append(appendedAssertions(assertionsPositive))
              .append(appendedDocJson(expJsonIdem))
              .append("    When I repeat the last request\n")
              .append("    Then the response status should be ").append(idemStatus).append("\n")
              .append(appendedDocJson(expJsonIdem));
        }

        // Pagination: only for GET and when 'page' param provided
        boolean isGet = call.getMethod() == ApiCall.Method.GET;
        String pageParam = null;
        if (!datasetProvided && isGet && includePagination && queryParams != null) {
            for (String[] kv : queryParams) {
                if (kv != null && kv.length >= 1 && kv[0] != null && kv[0].equalsIgnoreCase("page")) {
                    pageParam = kv[0];
                    break;
                }
            }
        }
        if (!datasetProvided && isGet && includePagination && pageParam != null) {
            String page1 = replaceOrAddQuery(path, pageParam, "1");
            String page2 = replaceOrAddQuery(path, pageParam, "2");
            sb.append("\n  @pagination\n  Scenario: Page 1 returns results\n")
              .append("    When I GET to \"").append(page1).append("\"\n")
              .append("    Then the response status should be 200\n")
              .append(appendedAssertions(assertionsPositive))
              .append(appendedDocJson(expJsonPositive))
              .append("    When I GET to \"").append(page1).append("\" and remember as \"page1\"\n")
              .append("    Then the response status should be 200\n")
              .append(appendedAssertions(assertionsPositive))
              .append(appendedDocJson(expJsonPositive))
              .append("\n  @pagination\n  Scenario: Page 2 differs from Page 1\n")
              .append("    When I GET to \"").append(page2).append("\"\n")
              .append("    Then the response status should be 200\n")
              .append(appendedAssertions(assertionsPositive))
              .append("    Then the response body should not equal remembered \"page1\"\n");
        }

        return sb.toString();
    }

    private static String escapeGherkin(String s) {
        String out = s;
        if (out == null) return "";
        // Normalize newlines and escape backslashes first
        out = out.replace("\r", "");
        out = out.replace("\\", "\\\\");
        // Trim stray trailing single quote often left by curl copy/paste
        out = out.replaceAll("\\s*'\\s*$", "");
        // Escape double quotes for inclusion in Gherkin quoted strings
        out = out.replace("\"", "\\\"");
        return out;
    }

    // Prefer single quotes around values when possible to avoid heavy escaping
    private static String quoteGherkinValue(String raw) {
        if (raw == null) return "\"\"";
        String cleaned = raw.replace("\r", "");
        // Strip common trailing artifacts copied from curl/JS extraction
        cleaned = cleaned.replaceAll("\\s*'\\s*\\\\\\\"\\s*$", ""); // trailing ' \"
        cleaned = cleaned.replaceAll("\\s*'\\s*\\\\\\s*$", "");         // trailing ' \\
        cleaned = cleaned.replaceAll("\\s*\\\\\\\"\\s*$", "");         // trailing \"
        cleaned = cleaned.replaceAll("\\s*'\\s*$", "");                       // trailing '
        cleaned = cleaned.replaceAll("\\s*\"\\s*$", "");                     // trailing "
        cleaned = cleaned.replaceAll("\\s*\\\\\\s*$", "");                 // trailing \\
        // Escape backslashes after trimming
        cleaned = cleaned.replace("\\", "\\\\");
        boolean hasSingle = cleaned.contains("'");
        boolean hasDouble = cleaned.contains("\"");
        if (!hasSingle) {
            // Safe to use single quotes
            return "'" + cleaned + "'";
        }
        // Use double quotes; escape internal double quotes
        String val = cleaned.replace("\"", "\\\"");
        return "\"" + val + "\"";
    }

    private static String appendedAssertions(java.util.List<String> steps) {
        if (steps == null || steps.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (String s : steps) out.append("    ").append(s).append("\n");
        return out.toString();
    }

    private static String appendedDocJson(String json) {
        if (json == null || json.isBlank()) return "";
        return "    Then the response json should equal:\n" +
               "      \"\"\"\n" + json + "\n      \"\"\"\n";
    }

    private static java.util.List<String> assertionsForCase(String caseName,
                                                            java.util.List<String> positive,
                                                            java.util.List<String> idem,
                                                            java.util.List<String> auth,
                                                            java.util.List<String> negative) {
        String key = caseName == null ? "" : caseName.toLowerCase();
        return switch (key) {
            case "positive", "pos", "+" -> positive;
            case "idempotency", "idemp" -> idem != null && !idem.isEmpty() ? idem : positive;
            case "auth", "unauth", "401" -> auth != null && !auth.isEmpty() ? auth : java.util.Collections.emptyList();
            case "negative", "negative_missing", "negative_type", "negative_null", "neg", "-" ->
                negative != null && !negative.isEmpty() ? negative : java.util.Collections.emptyList();
            case "pagination", "paging" -> positive;
            default -> positive;
        };
    }

    private static String replaceOrAddQuery(String urlPathWithQuery, String key, String value) {
        int qIdx = urlPathWithQuery.indexOf('?');
        if (qIdx < 0) {
            return urlPathWithQuery + "?" + enc(key) + "=" + enc(value);
        }
        String path = urlPathWithQuery.substring(0, qIdx);
        String q = urlPathWithQuery.substring(qIdx + 1);
        String[] parts = q.split("&");
        boolean replaced = false;
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            int idx = p.indexOf('=');
            String k = idx >= 0 ? p.substring(0, idx) : p;
            String v = idx >= 0 ? p.substring(idx + 1) : "";
            if (k.equalsIgnoreCase(key)) {
                v = enc(value);
                replaced = true;
            }
            if (sb.length() > 0) sb.append('&');
            sb.append(k).append(idx >= 0 ? "=" + v : "");
        }
        if (!replaced) {
            if (sb.length() > 0) sb.append('&');
            sb.append(enc(key)).append("=").append(enc(value));
        }
        return path + "?" + sb;
    }

    private static String enc(String s){
        return java.net.URLEncoder.encode(s == null ? "" : s, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static java.util.Map<String, java.util.List<Integer>> groupRowsByCase(String datasetCsvPath) {
        java.util.Map<String, java.util.List<Integer>> out = new java.util.LinkedHashMap<>();
        try {
            java.nio.file.Path p = java.nio.file.Paths.get("src/test/resources").resolve(datasetCsvPath);
            try (java.io.Reader rd = java.nio.file.Files.newBufferedReader(p);
                 com.opencsv.CSVReader reader = new com.opencsv.CSVReader(rd)) {
                java.util.List<String[]> rows = reader.readAll();
                if (rows.isEmpty()) return out;
                String[] headers = rows.get(0);
                int idx = -1;
                for (int i = 0; i < headers.length; i++) {
                    if (headers[i] != null && headers[i].trim().equalsIgnoreCase("__case")) { idx = i; break; }
                }
                // if no __case, default all rows to positive
                if (idx < 0) {
                    java.util.List<Integer> all = new java.util.ArrayList<>();
                    for (int r = 1; r < rows.size(); r++) {
                        if (isBlankRow(rows.get(r))) continue;
                        all.add(r);
                    }
                    out.put("positive", all);
                    return out;
                }
                for (int r = 1; r < rows.size(); r++) {
                    String[] row = rows.get(r);
                    if (isBlankRow(row)) continue;
                    String v = idx < row.length ? row[idx] : null;
                    String key = (v == null || v.isBlank()) ? "positive" : v.trim().toLowerCase();
                    out.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(r);
                }
            }
        } catch (Exception ignored) { }
        return out;
    }

    private static boolean isBlankRow(String[] row) {
        if (row == null) return true;
        for (String cell : row) {
            if (cell != null && !cell.trim().isEmpty()) return false;
        }
        return true;
    }

    private static String mapCaseToTag(String caseName) {
        String c = caseName == null ? "" : caseName.toLowerCase();
        return switch (c) {
            case "positive", "pos", "+" -> "@positive";
            case "negative", "neg", "-" -> "@negative";
            case "auth", "unauth", "401" -> "@auth";
            case "pagination", "paging" -> "@pagination";
            case "idempotency", "idemp" -> "@idempotency";
            default -> "@case-" + slugify(c.isBlank() ? "default" : c);
        };
    }

    private static String capitalize(String s) {
        if (s == null || s.isBlank()) return "";
        return s.substring(0,1).toUpperCase() + s.substring(1);
    }
}
