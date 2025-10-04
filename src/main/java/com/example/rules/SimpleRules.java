package com.example.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SimpleRules {
    private static final ObjectMapper M = new ObjectMapper();

    public static class NegativeCase {
        public final String name;
        public final String jsonBody;
        public final int expectedStatus;
        public NegativeCase(String name, String jsonBody, int expectedStatus) {
            this.name = name; this.jsonBody = jsonBody; this.expectedStatus = expectedStatus;
        }
    }

    private record PathValue(String path, JsonNode value) {}

    public static List<NegativeCase> deriveMissingFieldCases(String json, int expectedStatus) {
        return deriveMissingFieldCases(json, expectedStatus, null);
    }

    public static List<NegativeCase> deriveMissingFieldCases(String json, int expectedStatus, java.util.List<String> onlyPaths) {
        List<NegativeCase> out = new ArrayList<>();
        if (json == null || json.isBlank()) return out;
        try {
            JsonNode node = M.readTree(json);
            if (!node.isObject()) return out;
            ObjectNode root = (ObjectNode) node;
            java.util.List<String> paths = new java.util.ArrayList<>();
            if (onlyPaths != null && !onlyPaths.isEmpty()) {
                for (String p : onlyPaths) if (p != null && !p.isBlank()) paths.add(p.trim());
            } else {
                collectLeafPaths(root, "", paths);
            }
            for (String path : paths) {
                ObjectNode copy = root.deepCopy();
                removeByPath(copy, path);
                String body = M.writerWithDefaultPrettyPrinter().writeValueAsString(copy);
                out.add(new NegativeCase("missing required field '" + path + "'", body, expectedStatus));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static List<NegativeCase> deriveTypeMismatchCases(String json, int expectedStatus) {
        List<NegativeCase> out = new ArrayList<>();
        if (json == null || json.isBlank()) return out;
        try {
            JsonNode node = M.readTree(json);
            if (!node.isObject()) return out;
            ObjectNode root = (ObjectNode) node;
            List<PathValue> leaves = collectLeafValues(root);
            for (PathValue pv : leaves) {
                JsonNode replacement = mismatchedValue(pv.value());
                if (replacement == null) continue;
                ObjectNode mutated = root.deepCopy();
                setByPath(mutated, pv.path(), replacement);
                String body = M.writerWithDefaultPrettyPrinter().writeValueAsString(mutated);
                out.add(new NegativeCase("invalid type for '" + pv.path() + "'", body, expectedStatus));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static List<NegativeCase> deriveNullInjectionCases(String json, int expectedStatus) {
        List<NegativeCase> out = new ArrayList<>();
        if (json == null || json.isBlank()) return out;
        try {
            JsonNode node = M.readTree(json);
            if (!node.isObject()) return out;
            ObjectNode root = (ObjectNode) node;
            List<PathValue> leaves = collectLeafValues(root);
            for (PathValue pv : leaves) {
                if (pv.value() == null || pv.value().isNull()) continue;
                ObjectNode mutated = root.deepCopy();
                setByPath(mutated, pv.path(), com.fasterxml.jackson.databind.node.NullNode.instance);
                String body = M.writerWithDefaultPrettyPrinter().writeValueAsString(mutated);
                out.add(new NegativeCase("null value for '" + pv.path() + "'", body, expectedStatus));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void collectLeafPaths(ObjectNode node, String prefix, java.util.List<String> out) {
        Iterator<String> it = node.fieldNames();
        while (it.hasNext()) {
            String f = it.next();
            JsonNode child = node.get(f);
            String path = prefix.isEmpty() ? f : prefix + "." + f;
            if (child != null && child.isObject()) {
                collectLeafPaths((ObjectNode) child, path, out);
            } else if (child != null && child.isArray()) {
                // If array of objects, dive into first element and collect using index 0
                if (child.size() > 0 && child.get(0) != null && child.get(0).isObject()) {
                    collectLeafPaths((ObjectNode) child.get(0), path + "[0]", out);
                } else {
                    out.add(path);
                }
            } else {
                out.add(path);
            }
        }
    }

    private static List<PathValue> collectLeafValues(ObjectNode node) {
        List<PathValue> out = new ArrayList<>();
        collectLeafValues(node, "", out);
        return out;
    }

    private static void collectLeafValues(JsonNode node, String prefix, List<PathValue> out) {
        if (node == null) return;
        if (node.isObject()) {
            Iterator<String> it = node.fieldNames();
            while (it.hasNext()) {
                String f = it.next();
                JsonNode child = node.get(f);
                String path = prefix.isEmpty() ? f : prefix + "." + f;
                collectLeafValues(child, path, out);
            }
        } else if (node.isArray()) {
            if (node.size() > 0) {
                JsonNode first = node.get(0);
                if (first != null && (first.isObject() || first.isArray())) {
                    collectLeafValues(first, prefix + "[0]", out);
                } else {
                    out.add(new PathValue(prefix, first));
                }
            }
        } else {
            out.add(new PathValue(prefix, node));
        }
    }

    private static JsonNode mismatchedValue(JsonNode original) {
        if (original == null || original.isNull()) return null;
        if (original.isTextual()) {
            return com.fasterxml.jackson.databind.node.IntNode.valueOf(123);
        }
        if (original.isNumber()) {
            return com.fasterxml.jackson.databind.node.TextNode.valueOf("invalid_number");
        }
        if (original.isBoolean()) {
            return com.fasterxml.jackson.databind.node.TextNode.valueOf("invalid_boolean");
        }
        if (original.isArray()) {
            return com.fasterxml.jackson.databind.node.TextNode.valueOf("invalid_array");
        }
        if (original.isObject()) {
            return com.fasterxml.jackson.databind.node.TextNode.valueOf("invalid_object");
        }
        return null;
    }

    private static void removeByPath(ObjectNode node, String dottedPath) {
        String[] parts = dottedPath.split("\\.");
        JsonNode cur = node;
        for (int i = 0; i < parts.length - 1; i++) {
            String token = parts[i];
            String name = token;
            Integer idx = null;
            if (token.contains("[")) {
                int b = token.indexOf('[');
                int e = token.indexOf(']', b+1);
                if (b >= 0 && e > b) {
                    name = token.substring(0, b);
                    try { idx = Integer.parseInt(token.substring(b+1, e)); } catch (Exception ignore) {}
                }
            }
            cur = cur.get(name);
            if (cur == null) return;
            if (idx != null && cur.isArray()) {
                cur = cur.get(idx);
            }
            if (cur == null) return;
        }
        String last = parts[parts.length - 1];
        String name = last;
        Integer idx = null;
        if (last.contains("[")) {
            int b = last.indexOf('[');
            int e = last.indexOf(']', b+1);
            if (b >= 0 && e > b) {
                name = last.substring(0, b);
                try { idx = Integer.parseInt(last.substring(b+1, e)); } catch (Exception ignore) {}
            }
        }
        if (idx == null) {
            if (cur instanceof ObjectNode on) on.remove(name);
        } else {
            JsonNode arrParent = cur.get(name);
            if (arrParent != null && arrParent.isArray()) {
                ((com.fasterxml.jackson.databind.node.ArrayNode) arrParent).remove(idx.intValue());
            }
        }
    }

    private static void setByPath(ObjectNode node, String dottedPath, JsonNode newValue) {
        String[] parts = dottedPath.split("\\.");
        JsonNode cur = node;
        for (int i = 0; i < parts.length - 1; i++) {
            String token = parts[i];
            NameIdx ni = parseNameIdx(token);
            cur = cur.get(ni.name);
            if (cur == null) return;
            if (ni.idx != null && cur.isArray()) {
                cur = cur.get(ni.idx);
                if (cur == null) return;
            }
        }
        NameIdx last = parseNameIdx(parts[parts.length - 1]);
        JsonNode parent = cur.get(last.name);
        if (last.idx == null) {
            if (cur instanceof ObjectNode on) on.set(last.name, newValue);
        } else if (parent != null && parent.isArray()) {
            com.fasterxml.jackson.databind.node.ArrayNode arr = (com.fasterxml.jackson.databind.node.ArrayNode) parent;
            while (arr.size() <= last.idx) {
                arr.add(com.fasterxml.jackson.databind.node.NullNode.instance);
            }
            arr.set(last.idx, newValue);
        }
    }

    private record NameIdx(String name, Integer idx) {}

    private static NameIdx parseNameIdx(String token) {
        if (token == null) return new NameIdx("", null);
        int b = token.indexOf('[');
        int e = token.indexOf(']', b + 1);
        if (b >= 0 && e > b) {
            String name = token.substring(0, b);
            Integer idx = null;
            try { idx = Integer.parseInt(token.substring(b + 1, e)); } catch (Exception ignore) {}
            return new NameIdx(name, idx);
        }
        return new NameIdx(token, null);
    }
}
