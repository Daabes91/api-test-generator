package com.example.curl;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CurlParser {
    private static final Pattern HEADER_P = Pattern.compile(
            "(?i)(?:-H|--header)\\s+(?:'|\")?\\s*([^:]+):\\s*(.*?)(?:'|\")?(?=\\s(?:-H|--header|--data(?:-raw|-binary)?|-d)\\b|$)",
            Pattern.DOTALL);
    private static final Pattern METHOD_P = Pattern.compile("(?i)(?:-X|--request)\\s+(GET|POST|PUT|PATCH|DELETE)");
    private static final Pattern DATA_P = Pattern.compile("--data(?:-raw|-binary)?\\s+'(.*?)(?<!\\\\)'|--data(?:-raw|-binary)?\\s+\"(.*?)(?<!\\\\)\"", Pattern.DOTALL);

    public static ApiCall parse(String curl) {
        if (curl == null) throw new IllegalArgumentException("curl is null");
        String s = curl.trim();
        if (!s.startsWith("curl")) throw new IllegalArgumentException("Not a curl command");

        ApiCall call = new ApiCall();

        // Method (robust)
        ApiCall.Method method = null;
        Matcher mm = METHOD_P.matcher(s);
        if (mm.find()) {
            method = ApiCall.Method.valueOf(mm.group(1).toUpperCase());
        }
        if (method == null) {
            // Fallback: manual scan for --request/-X even with odd whitespace/newlines
            String low = s.toLowerCase();
            int idx = low.indexOf("--request");
            if (idx >= 0) {
                String tail = low.substring(idx + 9).trim();
                String word = tail.split("\\s+")[0];
                method = parseMethodWord(word);
            }
        }
        if (method == null) {
            String low = s.toLowerCase();
            int idx = low.indexOf("-x ");
            if (idx >= 0) {
                String tail = low.substring(idx + 3).trim();
                String word = tail.split("\\s+")[0];
                method = parseMethodWord(word);
            }
        }
        if (method == null) {
            if (s.contains("--data") || s.contains("--data-raw") || s.contains("--data-binary") || s.contains("-d ")) {
                method = ApiCall.Method.POST;
            } else {
                method = ApiCall.Method.GET;
            }
        }
        call.setMethod(method);

        // Headers (iterate robustly)
        Matcher hm = HEADER_P.matcher(s);
        while (hm.find()) {
            String name = hm.group(1).trim();
            String value = hm.group(2).trim();
            value = stripQuotes(value);
            call.getHeaders().put(name, value);
        }

        // Body
        String body = findData(s);
        if (body == null) {
            // Try -d "..."
            body = findDataShortD(s);
        }
        if (body != null) call.setBody(body);

        // URL: exclude data payloads to avoid picking URLs inside JSON bodies (e.g., avatar links)
        String sNoData = stripDataSections(s);
        call.setUrl(extractUrlPreferFirst(sNoData));

        // Auth
        String authHeader = call.getHeaders().getOrDefault("Authorization", call.getHeaders().get("authorization"));
        if (authHeader != null && authHeader.toLowerCase().startsWith("bearer ")) {
            call.setAuthType("Bearer");
        } else if (authHeader != null && authHeader.toLowerCase().startsWith("basic ")) {
            call.setAuthType("Basic");
        } else {
            call.setAuthType("None");
        }

        return call;
    }

    private static String extractUrlPreferFirst(String s) {
        // grab the first http(s) URL outside data payloads
        List<String> candidates = new ArrayList<>();
        Matcher q = Pattern.compile("(https?://[^'\"\s]+)").matcher(s);
        while (q.find()) candidates.add(q.group(1));
        if (!candidates.isEmpty()) return candidates.get(0);
        Matcher q2 = Pattern.compile("['\"](https?://[^'\"]+)['\"]").matcher(s);
        while (q2.find()) candidates.add(q2.group(1));
        if (!candidates.isEmpty()) return candidates.get(0);
        throw new IllegalArgumentException("URL not found in curl");
    }

    // Remove --data / --data-raw / --data-binary and -d payloads so we don't scan URLs inside bodies
    private static String stripDataSections(String s) {
        String out = s;
        Matcher m = DATA_P.matcher(out);
        out = m.replaceAll("");
        Matcher m2 = Pattern.compile("-d\\s+'(.*?)(?<!\\\\)'|-d\\s+\"(.*?)(?<!\\\\)\"", Pattern.DOTALL).matcher(out);
        out = m2.replaceAll("");
        return out;
    }

    private static String stripQuotes(String v) {
        if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    private static String findData(String s) {
        Matcher m = DATA_P.matcher(s);
        if (m.find()) {
            String g1 = m.group(1);
            String g2 = m.group(2);
            return g1 != null ? g1 : g2;
        }
        return null;
    }

    private static String findDataShortD(String s) {
        Matcher m = Pattern.compile("-d\\s+'(.*?)(?<!\\\\)'|-d\\s+\"(.*?)(?<!\\\\)\"", Pattern.DOTALL).matcher(s);
        if (m.find()) {
            String g1 = m.group(1);
            String g2 = m.group(2);
            return g1 != null ? g1 : g2;
        }
        return null;
    }

    private static ApiCall.Method parseMethodWord(String word){
        if (word == null || word.isEmpty()) return null;
        word = word.replaceAll("[^a-z]", "").toUpperCase();
        try { return ApiCall.Method.valueOf(word); } catch (Exception ignore) { return null; }
    }
}
