package support;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.specification.RequestSpecification;

import java.util.HashMap;
import java.util.Map;

public class AuthContext {
    private static final ThreadLocal<RequestSpecification> SPEC = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, String>> HEADERS = ThreadLocal.withInitial(HashMap::new);
    private static final ThreadLocal<String> BASE_URL = new ThreadLocal<>();

    public static void setBaseUrl(String baseUrl) {
        BASE_URL.set(baseUrl);
        rebuildSpec();
    }

    public static String getBaseUrl() {
        return BASE_URL.get();
    }

    public static void setBearerFromEnv(String envVar) {
        String token = System.getenv(envVar);
        if (token == null || token.isEmpty()) {
            // fallback to properties file
            token = Config.get(envVar);
        }
        if (token == null || token.isEmpty()) {
            throw new IllegalStateException("Missing token for key: " + envVar + " (env or properties)");
        }
        HEADERS.get().put("Authorization", "Bearer " + token);
        rebuildSpec();
    }

    public static void removeAuthHeader() {
        HEADERS.get().remove("Authorization");
        rebuildSpec();
    }

    public static void addHeader(String name, String value) {
        HEADERS.get().put(name, value);
        rebuildSpec();
    }

    public static java.util.Map<String, String> getHeaders() {
        return new java.util.HashMap<>(HEADERS.get());
    }

    public static void clearHeaders() {
        HEADERS.get().clear();
        rebuildSpec();
    }

    public static RequestSpecification spec() {
        if (SPEC.get() == null) rebuildSpec();
        return SPEC.get();
    }

    private static void rebuildSpec() {
        RequestSpecBuilder b = new RequestSpecBuilder();
        String base = BASE_URL.get();
        if (base != null && !base.isBlank()) {
            b.setBaseUri(base);
        }
        Map<String, String> h = HEADERS.get();
        for (Map.Entry<String, String> e : h.entrySet()) {
            b.addHeader(e.getKey(), e.getValue());
        }
        b.log(LogDetail.METHOD).log(LogDetail.URI).log(LogDetail.BODY);
        SPEC.set(b.build());
    }
}
