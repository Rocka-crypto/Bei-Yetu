package com.beiyetu.app.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Tiny CSV reader (header row, quoted fields, # comment lines). */
public final class Csv {
    private Csv() { }

    /** Each row becomes a map keyed by the lower-case header names, plus "_line" (the line number). */
    public static List<Map<String, String>> parse(String text, List<String> errors) {
        List<Map<String, String>> rows = new ArrayList<>();
        if (text == null) return rows;
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        String[] lines = text.split("\\r?\\n");
        List<String> header = null;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().isEmpty() || line.trim().startsWith("#")) continue;
            List<String> cells = splitLine(line);
            if (header == null) {
                header = new ArrayList<>();
                for (String h : cells) header.add(h.toLowerCase(Locale.ROOT).trim());
                continue;
            }
            if (cells.size() > header.size()) {
                errors.add("Row " + (i + 1) + ": too many columns (put names that contain commas in \"quotes\")");
                continue;
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) {
                row.put(header.get(c), c < cells.size() ? cells.get(c) : "");
            }
            row.put("_line", String.valueOf(i + 1));
            rows.add(row);
        }
        return rows;
    }

    public static List<String> splitLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString().trim());
        return out;
    }
}