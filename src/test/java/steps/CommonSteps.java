package steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import support.AuthContext;
import support.CsvLoader;
import support.SchemaUtils;
import support.World;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;

public class CommonSteps {

    private Response lastResponse;

    @Given("the API base url is {string}")
    public void givenBaseUrlEnv(String value) {
        String base = resolveBaseUrl(value);
        if (base == null || base.isBlank()) {
            throw new IllegalStateException("Base URL is not set (value=" + value + ")");
        }
        AuthContext.setBaseUrl(base);
    }

    private String resolveBaseUrl(String value) {
        if (value == null) return null;
        String v = value.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\$\\{([A-Za-z0-9_\\.\\-]+)\\}$").matcher(v);
        if (m.find()) {
            String key = m.group(1);
            // Prefer environment variable, then properties
            String fromEnv = System.getenv(key);
            if (fromEnv != null && !fromEnv.isBlank()) return fromEnv;
            if ("BASE_URL".equalsIgnoreCase(key)) {
                // Historical fallback keys
                String fromProps = support.Config.getFirst("BASE_URL", "base.url");
                if (fromProps != null && !fromProps.isBlank()) return fromProps;
            }
            String fromProps = support.Config.get(key);
            if (fromProps != null && !fromProps.isBlank()) return fromProps;
            return null;
        }
        return v;
    }

    @Then("the response json path {string} should equal {string}")
    public void jsonPathShouldEqual(String path, String expected) {
        String body = lastResponse.asString();
        Object actual = readJsonPath(body, path);
        String act = actual == null ? null : String.valueOf(actual);
        assertThat("json path " + path, act, equalTo(expected));
    }

    @Then("the response json path {string} should contain {string}")
    public void jsonPathShouldContain(String path, String expectedPart) {
        String body = lastResponse.asString();
        Object actual = readJsonPath(body, path);
        String act = actual == null ? null : String.valueOf(actual);
        if (act == null) throw new AssertionError("json path not found: " + path);
        org.hamcrest.MatcherAssert.assertThat("json path contains", act, containsString(expectedPart));
    }

    @Then("the response json path {string} should exist")
    public void jsonPathShouldExist(String path) {
        String body = lastResponse.asString();
        Object actual = readJsonPath(body, path);
        if (actual == null) throw new AssertionError("json path not found: " + path);
    }

    @Then("the response header {string} should equal {string}")
    public void headerShouldEqual(String name, String expected) {
        String actual = lastResponse.getHeader(name);
        assertThat("header " + name, actual, equalTo(expected));
    }

    @Then("the response header {string} should exist")
    public void headerShouldExist(String name) {
        String actual = lastResponse.getHeader(name);
        if (actual == null) throw new AssertionError("Expected header not present: " + name);
    }

    @Then("the response body should contain {string}")
    public void bodyShouldContain(String text) {
        String body = lastResponse.asString();
        org.hamcrest.MatcherAssert.assertThat("body should contain", body, containsString(text));
    }

    @Then("the response body should match regex {string}")
    public void bodyShouldMatchRegex(String pattern) {
        String body = lastResponse.asString();
        org.hamcrest.MatcherAssert.assertThat("body should match regex", body, matchesPattern(pattern));
    }

    @Then("the response json should equal {string}")
    public void jsonShouldEqual(String expectedJson) throws Exception {
        String body = lastResponse.asString();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode expected = mapper.readTree(expectedJson);
        com.fasterxml.jackson.databind.JsonNode actual = mapper.readTree(body);
        stripDynamicFields(expected);
        stripDynamicFields(actual);
        if (!expected.equals(actual)) {
            throw new AssertionError("JSON mismatch\nExpected: " + expected.toString() + "\nActual:   " + actual.toString());
        }
    }

    @Then("the response json should equal:")
    public void jsonShouldEqualDoc(String docString) throws Exception {
        String body = lastResponse.asString();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode expected = mapper.readTree(docString);
        com.fasterxml.jackson.databind.JsonNode actual = mapper.readTree(body);
        stripDynamicFields(expected);
        stripDynamicFields(actual);
        if (!expected.equals(actual)) {
            throw new AssertionError("JSON mismatch\nExpected: " + expected.toString() + "\nActual:   " + actual.toString());
        }
    }

    @Then("the response json should equal from csv column {string} row {int}")
    public void jsonShouldEqualFromCsv(String columnName, int row) throws Exception {
        String csvPath = World.recall("__current_csv_path");
        if (csvPath == null) throw new IllegalStateException("No dataset CSV used in this scenario. Ensure you used the dataset-driven step first.");
        String val = CsvLoader.valueFromCsv(csvPath, row, columnName);
        if (val == null || val.isBlank()) throw new IllegalArgumentException("No value found in column '" + columnName + "' for row " + row);
        jsonShouldEqual(expandPlaceholders(val));
    }

    private static final java.util.Set<String> DYNAMIC_KEYS = java.util.Set.of(
            "fingerprint"
    );

    private void stripDynamicFields(com.fasterxml.jackson.databind.JsonNode node) {
        stripDynamicFields(node, "$" );
    }

    private void stripDynamicFields(com.fasterxml.jackson.databind.JsonNode node, String currentPath) {
        if (node == null) return;
        if (node.isObject()) {
            com.fasterxml.jackson.databind.node.ObjectNode on = (com.fasterxml.jackson.databind.node.ObjectNode) node;
            java.util.Iterator<String> it = on.fieldNames();
            java.util.List<String> toRemove = new java.util.ArrayList<>();
            while (it.hasNext()) {
                String f = it.next();
                String childPath = currentPath.equals("$") ? "$" + "." + f : currentPath + "." + f;
                if (shouldIgnoreField(f, childPath)) {
                    toRemove.add(f);
                } else {
                    stripDynamicFields(on.get(f), childPath);
                }
            }
            for (String f : toRemove) on.remove(f);
        } else if (node.isArray()) {
            com.fasterxml.jackson.databind.node.ArrayNode arr = (com.fasterxml.jackson.databind.node.ArrayNode) node;
            for (int i = arr.size() - 1; i >= 0; i--) {
                String childPath = currentPath + "[" + i + "]";
                if (shouldIgnorePath(childPath)) {
                    arr.remove(i);
                } else {
                    stripDynamicFields(arr.get(i), childPath);
                }
            }
        }
    }

    private boolean shouldIgnoreField(String fieldName, String rawPath) {
        if (DYNAMIC_KEYS.contains(fieldName)) return true;
        return shouldIgnorePath(rawPath);
    }

    private boolean shouldIgnorePath(String rawPath) {
        String normalized = normalizeJsonPath(rawPath);
        return support.World.ignoredJsonPaths().contains(normalized);
    }

    // ----- Length validations -----
    @Then("the response json path {string} length should be {int}")
    public void jsonPathLengthEq(String path, int expectedLen) {
        int len = extractLengthFromJsonPath(path);
        org.junit.jupiter.api.Assertions.assertEquals(expectedLen, len, "length mismatch at path: " + path);
    }

    @Then("the response json path {string} length should be at least {int}")
    public void jsonPathLengthGte(String path, int minLen) {
        int len = extractLengthFromJsonPath(path);
        org.junit.jupiter.api.Assertions.assertTrue(len >= minLen, "length < min at path: " + path + ", got=" + len);
    }

    @Then("the response json path {string} length should be at most {int}")
    public void jsonPathLengthLte(String path, int maxLen) {
        int len = extractLengthFromJsonPath(path);
        org.junit.jupiter.api.Assertions.assertTrue(len <= maxLen, "length > max at path: " + path + ", got=" + len);
    }

    private int extractLengthFromJsonPath(String path) {
        String body = lastResponse.asString();
        Object value = readJsonPath(body, path);
        if (value == null) throw new AssertionError("json path not found: " + path);
        if (value instanceof java.util.Collection<?> c) return c.size();
        if (value instanceof java.util.Map<?,?> m) return m.size();
        String s = String.valueOf(value);
        return s != null ? s.length() : 0;
    }

    // ----- Numeric validations -----
    @Then("the response json path {string} should be a number")
    public void jsonPathIsNumber(String path) {
        Number n = getJsonNumber(path);
        if (n == null) throw new AssertionError("value at path is not a number: " + path);
    }

    @Then("the response json path {string} should be an integer")
    public void jsonPathIsInteger(String path) {
        Number n = getJsonNumber(path);
        if (n == null || (n.doubleValue() % 1) != 0.0) {
            throw new AssertionError("value at path is not an integer: " + path + ", got=" + (n == null ? "null" : n));
        }
    }

    @Then("the response json path {string} should be >= {double}")
    public void jsonPathGte(String path, double min) {
        Number n = getJsonNumber(path);
        if (n == null) throw new AssertionError("value at path is not a number: " + path);
        org.junit.jupiter.api.Assertions.assertTrue(n.doubleValue() >= min, "value < min at path: " + path + ", got=" + n + ", min=" + min);
    }

    @Then("the response json path {string} should be <= {double}")
    public void jsonPathLte(String path, double max) {
        Number n = getJsonNumber(path);
        if (n == null) throw new AssertionError("value at path is not a number: " + path);
        org.junit.jupiter.api.Assertions.assertTrue(n.doubleValue() <= max, "value > max at path: " + path + ", got=" + n + ", max=" + max);
    }

    @Then("the response json path {string} should be between {double} and {double}")
    public void jsonPathBetween(String path, double min, double max) {
        Number n = getJsonNumber(path);
        if (n == null) throw new AssertionError("value at path is not a number: " + path);
        double v = n.doubleValue();
        org.junit.jupiter.api.Assertions.assertTrue(v >= min && v <= max, "value not in range at path: " + path + ", got=" + v + ", range=" + min + ".." + max);
    }

    private Number getJsonNumber(String path) {
        String body = lastResponse.asString();
        Object value = readJsonPath(body, path);
        if (value instanceof Number) return (Number) value;
        if (value instanceof String s) {
            try { return Double.parseDouble(s.trim()); } catch (Exception ignore) {}
        }
        return null;
    }

    @Given("I use bearer token from env {string}")
    public void useBearerTokenFromEnv(String envVar) {
        AuthContext.setBearerFromEnv(envVar);
    }

    @Given("I remove authorization header")
    public void removeAuthorizationHeader() {
        AuthContext.removeAuthHeader();
    }

    @When("I {word} to {string}")
    public void iVerbTo(String verb, String path) {
        verb = verb.toUpperCase();
        String expPath = expandPlaceholders(path);
        World.set(verb, expPath, null);
        switch (verb) {
            case "GET" -> lastResponse = RestAssured.given().spec(AuthContext.spec()).when().get(expPath);
            case "DELETE" -> lastResponse = RestAssured.given().spec(AuthContext.spec()).when().delete(expPath);
            default -> throw new IllegalArgumentException("Unsupported verb without body: " + verb);
        }
        support.AllureUtils.attachRequest(verb, expPath, null);
        support.AllureUtils.attachResponse(lastResponse);
    }

    @When("I {word} to {string} with json body:")
    public void iVerbToWithJsonBody(String verb, String path, String docString) {
        String expPath = expandPlaceholders(path);
        String expBody = expandPlaceholders(docString);
        World.set(verb.toUpperCase(), expPath, expBody);
        lastResponse = RestAssured.given()
                .spec(AuthContext.spec())
                .contentType(ContentType.JSON)
                .body(expBody)
                .when()
                .request(verb.toUpperCase(), expPath);
        support.AllureUtils.attachRequest(verb.toUpperCase(), expPath, expBody);
        support.AllureUtils.attachResponse(lastResponse);
    }

    @When("I {word} to {string} with json body from csv {string} row {int}")
    public void iVerbToWithJsonBodyFromCsv(String verb, String path, String csvPath, int row) throws Exception {
        String json = CsvLoader.jsonFromCsvRow(csvPath, row);
        String effectivePath = path;
        // Allow per-row path override via __path
        try {
            String overridePath = CsvLoader.valueFromCsv(csvPath, row, "__path");
            if (overridePath != null && !overridePath.isBlank()) {
                effectivePath = overridePath.trim();
            }
            // Allow per-row base URL override via __base_url or __base (admin|store)
            String baseUrl = CsvLoader.valueFromCsv(csvPath, row, "__base_url");
            if (baseUrl != null && !baseUrl.isBlank()) {
                support.AuthContext.setBaseUrl(baseUrl.trim());
            } else {
                String baseKey = CsvLoader.valueFromCsv(csvPath, row, "__base");
                if (baseKey != null && !baseKey.isBlank()) {
                    String key = baseKey.trim().toLowerCase();
                    if (key.equals("admin")) {
                        String b = support.Config.getFirst("BASE_URL_ADMIN", "base.url.admin");
                        if (b != null && !b.isBlank()) support.AuthContext.setBaseUrl(b);
                    } else if (key.equals("store")) {
                        String b = support.Config.getFirst("BASE_URL_STORE", "base.url.store");
                        if (b != null && !b.isBlank()) support.AuthContext.setBaseUrl(b);
                    }
                }
            }
        } catch (Exception ignored) {}

        World.set(verb.toUpperCase(), effectivePath, json);
        World.remember("__current_csv_path", csvPath);
        // Dataset-driven auth control via __token_env / __auth columns
        try {
            String tokenEnv = CsvLoader.valueFromCsv(csvPath, row, "__token_env");
            if (tokenEnv != null && !tokenEnv.isBlank()) {
                AuthContext.setBearerFromEnv(tokenEnv.trim());
            }
            String authMeta = CsvLoader.valueFromCsv(csvPath, row, "__auth");
            if (authMeta != null && !authMeta.isBlank()) {
                String v = authMeta.trim();
                String low = v.toLowerCase();
                if (low.equals("none") || low.equals("missing") || low.equals("omit")) {
                    AuthContext.removeAuthHeader();
                } else if (low.equals("invalid")) {
                    AuthContext.addHeader("Authorization", "Bearer invalid");
                } else if (low.startsWith("env:")) {
                    String env = v.substring(4).trim();
                    if (!env.isEmpty()) AuthContext.setBearerFromEnv(env);
                } else {
                    // allow explicit override e.g., "Bearer abc" or any custom value
                    AuthContext.addHeader("Authorization", v);
                }
            }
            // Per-row headers via __headers in format: "Name: Value|Name2: Value2"
            String extraHeaders = CsvLoader.valueFromCsv(csvPath, row, "__headers");
            if (extraHeaders != null && !extraHeaders.isBlank()) {
                String[] pairs = extraHeaders.split("\\|");
                for (String p : pairs) {
                    if (p == null || p.isBlank()) continue;
                    int idx = p.indexOf(":");
                    if (idx <= 0) continue;
                    String hName = p.substring(0, idx).trim();
                    String hVal = p.substring(idx + 1).trim();
                    if (!hName.isEmpty()) AuthContext.addHeader(hName, hVal);
                }
            }
        } catch (Exception ignored) {}
        // Expand placeholders in final path and body
        String expPath = expandPlaceholders(effectivePath);
        String expJson = expandPlaceholders(json);
        switch (verb.toUpperCase()) {
            case "GET" -> lastResponse = RestAssured.given().spec(AuthContext.spec()).when().get(expPath);
            case "DELETE" -> lastResponse = RestAssured.given().spec(AuthContext.spec()).when().delete(expPath);
            default -> lastResponse = RestAssured.given()
                    .spec(AuthContext.spec())
                    .contentType(ContentType.JSON)
                    .body(expJson)
                    .when()
                    .request(verb.toUpperCase(), expPath);
        }
        support.AllureUtils.attachRequest(verb.toUpperCase(), expPath, expJson);
        support.AllureUtils.attachResponse(lastResponse);
    }

    @Then("the response status should be {int}")
    public void theResponseStatusShouldBe(int expected) {
        assertThat("status code", lastResponse.statusCode(), equalTo(expected));
    }

    @Then("the response should match schema {string}")
    public void theResponseShouldMatchSchema(String schemaPath) {
        lastResponse.then().assertThat().body(SchemaUtils.matchesSchema(schemaPath));
    }

    @Then("the response status should be from csv column {string} row {int}")
    public void theResponseStatusShouldBeFromCsv(String columnName, int row) throws Exception {
        String csvPath = World.recall("__current_csv_path");
        if (csvPath == null) throw new IllegalStateException("No dataset CSV used in this scenario. Ensure you used the '... from csv " +
                "\"<path>\" row <row>' step first.");
        String val = CsvLoader.valueFromCsv(csvPath, row, columnName);
        int expected;
        try { expected = Integer.parseInt(val.trim()); } catch (Exception e) {
            throw new IllegalArgumentException("Expected an integer status in column '" + columnName + "' but got: " + val);
        }
        theResponseStatusShouldBe(expected);
    }

    @Given("I add header {string} with value {string}")
    public void iAddHeader(String name, String value) {
        AuthContext.addHeader(name, value);
    }

    @Given("I override header {string} with value {string}")
    public void iOverrideHeader(String name, String value) {
        AuthContext.addHeader(name, value);
    }

    @When("I repeat the last request")
    public void iRepeatTheLastRequest() {
        String verb = World.verb();
        String path = World.path();
        String body = World.body();
        if (verb == null || path == null) throw new IllegalStateException("No previous request to repeat");
        if (body == null) {
            switch (verb) {
                case "GET" -> lastResponse = RestAssured.given().spec(AuthContext.spec()).when().get(path);
                case "DELETE" -> lastResponse = RestAssured.given().spec(AuthContext.spec()).when().delete(path);
                default -> lastResponse = RestAssured.given().spec(AuthContext.spec()).when().request(verb, path);
            }
            support.AllureUtils.attachRequest(verb, path, null);
        } else {
            lastResponse = RestAssured.given()
                    .spec(AuthContext.spec())
                    .contentType(ContentType.JSON)
                    .body(body)
                    .when()
                    .request(verb, path);
            support.AllureUtils.attachRequest(verb, path, body);
        }
        support.AllureUtils.attachResponse(lastResponse);
    }

    @When("I GET to {string} and remember as {string}")
    public void iGetAndRemember(String path, String name) {
        String expPath = expandPlaceholders(path);
        lastResponse = RestAssured.given().spec(AuthContext.spec()).when().get(expPath);
        World.set("GET", expPath, null);
        World.remember(name, lastResponse.asString());
        support.AllureUtils.attachRequest("GET", expPath, null);
        support.AllureUtils.attachResponse(lastResponse);
    }

    @Then("the response body should not equal remembered {string}")
    public void bodyShouldNotEqualRemembered(String name) {
        String remembered = World.recall(name);
        if (remembered == null) throw new IllegalStateException("No remembered body for key: " + name);
        String current = lastResponse.asString();
        org.junit.jupiter.api.Assertions.assertNotEquals(remembered, current, "Response bodies are equal but expected different pages");
    }

    @Then("the response body should equal remembered {string}")
    public void bodyShouldEqualRemembered(String name) {
        String remembered = World.recall(name);
        if (remembered == null) throw new IllegalStateException("No remembered body for key: " + name);
        String current = lastResponse.asString();
        org.junit.jupiter.api.Assertions.assertEquals(remembered, current, "Response bodies differ");
    }

    @Then("remember json path {string} as {string}")
    public void rememberJsonPath(String path, String key) {
        String body = lastResponse.asString();
        Object value = readJsonPath(body, path);
        if (value == null) throw new IllegalStateException("json path not found: " + path);
        World.remember(key, String.valueOf(value));
    }

    @Given("I ignore response json path {string}")
    public void ignoreResponseJsonPath(String path) {
        String normalized = normalizeJsonPath(path);
        World.addIgnoredJsonPath(normalized);
    }

    private Object readJsonPath(String body, String rawPath) {
        if (rawPath == null || rawPath.isBlank()) return null;
        String normalized = normalizeJsonPath(rawPath);
        return io.restassured.path.json.JsonPath.from(body).get(normalized);
    }

    private String normalizeJsonPath(String rawPath) {
        String normalized = rawPath.trim();
        if (normalized.startsWith("$.")) {
            normalized = normalized.substring(2);
        } else if (normalized.startsWith("$")) {
            normalized = normalized.substring(1);
        }
        normalized = normalized.replaceAll("\\['([^']+)']", ".$1");
        normalized = normalized.replaceAll("\\[\"([^\"]+)\"]", ".$1");
        while (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private String expandPlaceholders(String in) {
        if (in == null) return null;
        String s = in;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\$\\{([A-Za-z0-9_\\.\\-]+)\\}").matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            String replacement = World.recall(key);
            if (replacement == null) {
                // Try env and config as fallback for general placeholders
                replacement = System.getenv(key);
                if (replacement == null) replacement = support.Config.get(key);
            }
            if (replacement == null) replacement = "";
            // Escape backslashes and dollars in replacement for Matcher
            replacement = replacement.replace("\\", "\\\\").replace("$", "\\$");
            m.appendReplacement(sb, replacement);
        }
        m.appendTail(sb);
        return sb.toString();
    }

}
