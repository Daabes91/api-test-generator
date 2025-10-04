package support;

import io.qameta.allure.Allure;
import io.restassured.response.Response;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class AllureUtils {
    private static String maskSensitive(String name, String value) {
        if (name == null) return value;
        String n = name.toLowerCase();
        if (n.equals("authorization") || n.contains("token") || n.contains("secret")) {
            if (value == null) return "<hidden>";
            if (value.toLowerCase().startsWith("bearer ")) return "Bearer <hidden>";
            return "<hidden>";
        }
        return value;
    }

    private static boolean allureReady() {
        try {
            Object lifecycle = Allure.getLifecycle();
            try {
                java.lang.reflect.Method m = lifecycle.getClass().getMethod("getCurrentTestCase");
                Object result = m.invoke(lifecycle);
                if (result instanceof java.util.Optional<?> opt) {
                    return opt.isPresent();
                }
                return result != null;
            } catch (NoSuchMethodException first) {
                try {
                    java.lang.reflect.Method m = lifecycle.getClass().getMethod("getCurrentTestCaseOrNull");
                    Object result = m.invoke(lifecycle);
                    return result != null;
                } catch (NoSuchMethodException ignored) {
                    return false;
                }
            }
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void attachRequest(String method, String path, String body) {
        if (!allureReady()) return;
        try {
            String base = AuthContext.getBaseUrl();
            String url = (base == null || base.isBlank()) ? path : base.replaceAll("/+$", "") + path;
            Allure.addAttachment("Request URL", "text/plain", url);
            Allure.addAttachment("Request Method", "text/plain", method);
            // Headers
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String,String> e : AuthContext.getHeaders().entrySet()) {
                sb.append(e.getKey()).append(": ")
                  .append(maskSensitive(e.getKey(), e.getValue()))
                  .append("\n");
            }
            if (sb.length() > 0) Allure.addAttachment("Request Headers", "text/plain", sb.toString());
            if (body != null && !body.isBlank()) {
                Allure.addAttachment("Request Body", "application/json", body, ".json");
            }
        } catch (Throwable ignore) {}
    }

    public static void attachResponse(Response resp) {
        if (!allureReady() || resp == null) return;
        try {
            Allure.addAttachment("Response Status", "text/plain", String.valueOf(resp.statusLine()));
            // Headers
            StringBuilder sb = new StringBuilder();
            for (var h : resp.getHeaders()) {
                sb.append(h.getName()).append(": ").append(h.getValue()).append("\n");
            }
            if (sb.length() > 0) Allure.addAttachment("Response Headers", "text/plain", sb.toString());
            String body = null;
            try { body = resp.getBody().asString(); } catch (Throwable ignore) {}
            if (body != null && !body.isBlank()) {
                // Try JSON first; if not JSON, attach as plain text
                String ct = resp.getContentType();
                if (ct != null && ct.toLowerCase().contains("json")) {
                    Allure.addAttachment("Response Body", "application/json", body, ".json");
                } else {
                    // still attach as text
                    Allure.addAttachment("Response Body", "text/plain", body);
                }
            }
        } catch (Throwable ignore) {}
    }
}
