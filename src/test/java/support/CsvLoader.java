package support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opencsv.CSVReader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public class CsvLoader {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String jsonFromCsvRow(String resourcePath, int rowIndex) throws Exception {
        try (InputStream is = CsvLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) throw new IllegalArgumentException("CSV not found: " + resourcePath);
            try (CSVReader reader = new CSVReader(new InputStreamReader(is))) {
                List<String[]> rows = reader.readAll();
                if (rows.isEmpty()) throw new IllegalArgumentException("CSV empty: " + resourcePath);
                String[] headers = rows.get(0);
                if (rowIndex <= 0 || rowIndex >= rows.size()) {
                    throw new IllegalArgumentException("Row index out of range: " + rowIndex);
                }
                String[] values = rows.get(rowIndex);
                ObjectNode node = MAPPER.createObjectNode();
                for (int i = 0; i < headers.length && i < values.length; i++) {
                    String h = headers[i];
                    if (h == null) continue;
                    // skip metadata columns that start with double underscore
                    if (h.startsWith("__")) continue;
                    String v = values[i];
                    applyValue(node, h, v);
                }
                return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
            }
        }
    }

    public static String valueFromCsv(String resourcePath, int rowIndex, String columnName) throws Exception {
        try (InputStream is = CsvLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) throw new IllegalArgumentException("CSV not found: " + resourcePath);
            try (CSVReader reader = new CSVReader(new InputStreamReader(is))) {
                java.util.List<String[]> rows = reader.readAll();
                if (rows.isEmpty()) throw new IllegalArgumentException("CSV empty: " + resourcePath);
                String[] headers = rows.get(0);
                int col = -1;
                for (int i = 0; i < headers.length; i++) {
                    if (headers[i] != null && headers[i].trim().equalsIgnoreCase(columnName.trim())) { col = i; break; }
                }
                if (col < 0) throw new IllegalArgumentException("Column not found: " + columnName);
                if (rowIndex <= 0 || rowIndex >= rows.size()) {
                    throw new IllegalArgumentException("Row index out of range: " + rowIndex);
                }
                String[] values = rows.get(rowIndex);
                return col < values.length ? values[col] : null;
            }
        }
    }

    private static void applyValue(ObjectNode root, String header, String raw) throws Exception {
        if (header == null || header.isBlank()) return;
        String[] parts = header.split("\\.");
        ObjectNode cursor = root;
        for (int pi = 0; pi < parts.length - 1; pi++) {
            String key = parts[pi];
            JsonNode existing = cursor.get(key);
            if (existing == null || !existing.isObject()) {
                ObjectNode child = MAPPER.createObjectNode();
                cursor.set(key, child);
                cursor = child;
            } else {
                cursor = (ObjectNode) existing;
            }
        }
        String leaf = parts[parts.length - 1];
        String v = raw;
        if (v == null || v.isBlank()) {
            cursor.putNull(leaf);
        } else if (v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false")) {
            cursor.put(leaf, Boolean.parseBoolean(v));
        } else if (v.matches("-?\\d+")) {
            cursor.put(leaf, Long.parseLong(v));
        } else if (v.matches("-?\\d+\\.\\d+")) {
            cursor.put(leaf, Double.parseDouble(v));
        } else if (v.startsWith("{") || v.startsWith("[")) {
            try {
                JsonNode sub = MAPPER.readTree(v);
                cursor.set(leaf, sub);
            } catch (Exception ex) {
                cursor.put(leaf, v);
            }
        } else {
            cursor.put(leaf, v);
        }
    }
}
