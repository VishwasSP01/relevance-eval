package io.github.vishwassp01.relevanceeval.clicks;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reads and parses click log CSV files into {@link ClickEvent} domain objects.
 * <p>
 * Expected CSV format:
 * <pre>
 *   query,documentId,position,clicked
 *   running shoes,SKU-1024,1,true
 *   running shoes,SKU-2048,2,false
 * </pre>
 */
public final class ClickLogReader {

    private ClickLogReader() {
        // Utility class
    }

    /**
     * Reads click events from the specified CSV file path.
     *
     * @param path path to the CSV file
     * @return parsed list of click events
     * @throws ClickLogParseException if the file has missing or invalid header, invalid position, or invalid clicked value
     * @throws IOException            if reading the file fails
     */
    public static List<ClickEvent> read(Path path) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("File does not exist: " + path);
        }

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return read(reader);
        }
    }

    /**
     * Reads click events from a {@link BufferedReader}.
     *
     * @param reader reader providing CSV lines
     * @return parsed list of click events
     * @throws ClickLogParseException if the content has missing or invalid header, invalid position, or invalid clicked value
     * @throws IOException            if an I/O error occurs
     */
    public static List<ClickEvent> read(BufferedReader reader) throws IOException {
        String firstLine = reader.readLine();
        if (firstLine == null) {
            throw new ClickLogParseException(1, "CSV file is empty; expected header row 'query,documentId,position,clicked'");
        }

        // Strip UTF-8 BOM if present
        firstLine = firstLine.replace("\uFEFF", "").trim();
        List<String> headerCols = parseCsvLine(firstLine);
        if (headerCols.size() != 4
                || !headerCols.get(0).trim().equals("query")
                || !headerCols.get(1).trim().equals("documentId")
                || !headerCols.get(2).trim().equals("position")
                || !headerCols.get(3).trim().equals("clicked")) {
            throw new ClickLogParseException(1, "Invalid or missing header row. Expected 'query,documentId,position,clicked', but found: '" + firstLine + "'");
        }

        List<ClickEvent> events = new ArrayList<>();
        String line;
        int lineNumber = 1;

        while ((line = reader.readLine()) != null) {
            lineNumber++;
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                throw new ClickLogParseException(lineNumber, "Encountered empty row");
            }

            List<String> cols;
            try {
                cols = parseCsvLine(line);
            } catch (Exception e) {
                throw new ClickLogParseException(lineNumber, "Malformed CSV row: " + e.getMessage());
            }

            if (cols.size() != 4) {
                throw new ClickLogParseException(lineNumber, "Expected 4 columns (query,documentId,position,clicked), but found " + cols.size() + ": '" + line + "'");
            }

            String query = cols.get(0).trim();
            String docId = cols.get(1).trim();
            String posStr = cols.get(2).trim();
            String clickedStr = cols.get(3).trim();

            if (query.isEmpty()) {
                throw new ClickLogParseException(lineNumber, "Query must not be blank");
            }
            if (docId.isEmpty()) {
                throw new ClickLogParseException(lineNumber, "DocumentId must not be blank");
            }

            int position;
            try {
                position = Integer.parseInt(posStr);
            } catch (NumberFormatException e) {
                throw new ClickLogParseException(lineNumber, "Invalid position '" + posStr + "'. Position must be a positive integer (>= 1)");
            }

            if (position < 1) {
                throw new ClickLogParseException(lineNumber, "Invalid position '" + posStr + "'. Position must be a positive integer (>= 1)");
            }

            if (!clickedStr.equalsIgnoreCase("true") && !clickedStr.equalsIgnoreCase("false")) {
                throw new ClickLogParseException(lineNumber, "Invalid clicked value '" + clickedStr + "'. Clicked must be 'true' or 'false'");
            }

            boolean clicked = Boolean.parseBoolean(clickedStr);
            events.add(new ClickEvent(query, docId, position, clicked));
        }

        return events;
    }

    /**
     * Parses a single CSV line into tokens, respecting double quotes and escaped quotes.
     *
     * @param line CSV row line
     * @return list of column string values
     */
    public static List<String> parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }

        if (inQuotes) {
            throw new IllegalArgumentException("Unclosed quotation mark in CSV row: " + line);
        }

        tokens.add(sb.toString());
        return tokens;
    }
}
