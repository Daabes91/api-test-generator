package com.example.cli;

import com.example.curl.ApiCall;
import com.example.curl.CurlParser;
import com.example.generator.FeatureGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class GenerateFromCurl {
    public static void main(String[] args) throws IOException {
        // Parse optional --out <dir> or --out=<dir>
        String outDirArg = null;
        List<String> curlParts = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--out") || a.equals("-o")) {
                if (i + 1 < args.length) {
                    outDirArg = args[++i];
                }
            } else if (a.startsWith("--out=")) {
                outDirArg = a.substring("--out=".length());
            } else {
                curlParts.add(a);
            }
        }

        String input;
        if (!curlParts.isEmpty()) {
            input = String.join(" ", curlParts);
        } else {
            System.out.println("Paste a curl and press Ctrl+D (Linux/macOS) or Ctrl+Z (Windows) to finish:");
            Scanner sc = new Scanner(System.in).useDelimiter("\\A");
            input = sc.hasNext() ? sc.next() : "";
        }

        ApiCall call = CurlParser.parse(input);
        // Pass headers from curl to the generator (excluding Authorization; token handled via env step)
        java.util.List<String[]> extraHeaders = new java.util.ArrayList<>();
        for (var e : call.getHeaders().entrySet()) {
            String name = e.getKey();
            if (name == null) continue;
            if (name.equalsIgnoreCase("authorization")) continue;
            String value = sanitizeHeaderValue(e.getValue());
            extraHeaders.add(new String[]{name, value});
        }
        java.util.List<String[]> queryParams = java.util.Collections.emptyList();
        String feature = FeatureGenerator.generateFeature(call, "API_TOKEN", extraHeaders, queryParams, 200);

        String uriPath = "/";
        try {
            uriPath = java.net.URI.create(call.getUrl()).getPath();
        } catch (Exception ignored) {
            // fall back to default below
        }
        if (uriPath == null || uriPath.isBlank()) {
            uriPath = "/";
        }
        String slug = FeatureGenerator.slugify(uriPath);
        String name = call.getMethod().name().toLowerCase() + "_" + slug + ".feature";

        Path outDir = Paths.get(outDirArg != null && !outDirArg.isBlank()
                ? outDirArg
                : "src/test/resources/features/generated");
        Files.createDirectories(outDir);
        Path out = outDir.resolve(name);
        Files.writeString(out, feature);
        System.out.println("Generated: " + out.toAbsolutePath());
    }

    private static String sanitizeHeaderValue(String v) {
        String cleaned = v == null ? "" : v.replace("\r", "");
        cleaned = cleaned.replaceAll("\\s*'\\s*\\\\\\\"\\s*$", ""); // trailing ' \"
        cleaned = cleaned.replaceAll("\\s*'\\s*\\\\\\s*$", "");         // trailing ' \\
        cleaned = cleaned.replaceAll("\\s*\\\\\\\"\\s*$", "");         // trailing \"
        cleaned = cleaned.replaceAll("\\s*'\\s*$", "");                       // trailing '
        cleaned = cleaned.replaceAll("\\s*\"\\s*$", "");                     // trailing "
        cleaned = cleaned.replaceAll("\\s*\\\\\\s*$", "");                 // trailing \\
        return cleaned.trim();
    }
}
