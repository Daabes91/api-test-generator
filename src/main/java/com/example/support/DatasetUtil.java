package com.example.support;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import com.opencsv.CSVReader;

public class DatasetUtil {
    public static String xlsxToCsv(InputStream in) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : null;
            if (sheet == null) return "";
            StringBuilder sb = new StringBuilder();
            for (Row row : sheet) {
                List<String> cells = new ArrayList<>();
                int last = row.getLastCellNum();
                for (int i = 0; i < last; i++) {
                    Cell c = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if (c == null) {
                        cells.add("");
                    } else {
                        switch (c.getCellType()) {
                            case BOOLEAN -> cells.add(Boolean.toString(c.getBooleanCellValue()));
                            case NUMERIC -> {
                                double v = c.getNumericCellValue();
                                if (v == Math.rint(v)) cells.add(Long.toString((long) v));
                                else cells.add(Double.toString(v));
                            }
                            case STRING -> cells.add(escapeCsv(c.getStringCellValue()));
                            case BLANK, _NONE, ERROR, FORMULA -> cells.add(escapeCsv(c.toString()));
                        }
                    }
                }
                sb.append(String.join(",", cells)).append("\n");
            }
            return sb.toString();
        }
    }

    public static int countCsvRows(InputStream in) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            int count = 0;
            boolean headerConsumed = false;
            String line;
            while ((line = br.readLine()) != null) {
                if (!headerConsumed) { headerConsumed = true; continue; }
                if (line.trim().isEmpty()) continue;
                count++;
            }
            return count;
        }
    }

    public static List<String> parseCsvHeader(InputStream in) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String first = br.readLine();
            if (first == null) return List.of();
            List<String> headers = new ArrayList<>();
            for (String part : first.split(",")) {
                String h = part == null ? "" : part.trim();
                if (h.startsWith("\"") && h.endsWith("\"")) {
                    h = h.substring(1, h.length()-1).replace("\"\"", "\"");
                }
                if (!h.isEmpty()) headers.add(h);
            }
            return headers;
        }
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        boolean needQuotes = s.contains(",") || s.contains("\n") || s.contains("\"");
        String out = s.replace("\"", "\"\"");
        return needQuotes ? ("\"" + out + "\"") : out;
    }

    public static Map<String, String> firstValueTypes(InputStream in) throws Exception {
        try (CSVReader reader = new CSVReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            List<String[]> rows = reader.readAll();
            Map<String, String> types = new LinkedHashMap<>();
            if (rows.isEmpty()) return types;
            String[] headers = rows.get(0);
            for (String h : headers) {
                if (h != null) types.put(h.trim(), null);
            }
            for (int r = 1; r < rows.size(); r++) {
                String[] vals = rows.get(r);
                if (vals == null) continue;
                boolean allBlank = true;
                for (String v : vals) {
                    if (v != null && !v.trim().isEmpty()) { allBlank = false; break; }
                }
                if (allBlank) continue;
                for (int c = 0; c < headers.length && c < vals.length; c++) {
                    String h = headers[c] == null ? "" : headers[c].trim();
                    if (!types.containsKey(h)) continue;
                    if (types.get(h) != null) continue; // already have a type
                    String v = vals[c];
                    if (v == null) continue;
                    v = v.trim();
                    if (v.isEmpty()) continue;
                    types.put(h, inferValueType(v));
                }
            }
            return types;
        }
    }

    public static String inferValueType(String v) {
        if (v == null || v.isBlank()) return "null";
        if ("true".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v)) return "boolean";
        if (v.matches("-?\\d+")) return "integer";
        if (v.matches("-?\\d+\\.\\d+")) return "number";
        if (v.startsWith("{") || v.startsWith("[")) return "json";
        return "string";
    }
}
