package com.example.logconsole.ui;

import com.example.logconsole.model.ParsedRecord;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LogHighlighter {
    private static final String RESET = "\033[0m";
    private static final String DIM = "\033[90m";
    private static final String SOURCE = "\033[35m";
    private static final String LOGGER = "\033[36m";
    private static final String ERROR = "\033[1;31m";
    private static final Pattern EXCEPTION = Pattern.compile("([A-Za-z_$][A-Za-z0-9_.$]*(?:Exception|Error))");

    private final boolean color;

    LogHighlighter(boolean color) { this.color = color; }

    String render(ParsedRecord record, boolean compact) {
        if (!color) return compact ? record.renderCompact() : record.renderRaw();
        if (!compact) return renderRaw(record);
        if (record.isLeadingUnmatched()) {
            return DIM + "[UNMATCHED " + record.getSource().getLabel() + "]" + RESET + " "
                    + join(record.getRawLines());
        }
        StringBuilder result = new StringBuilder();
        result.append(DIM).append(record.getTimestampText()).append(RESET).append(' ')
                .append(level(record.getLevel())).append(" [").append(SOURCE)
                .append(record.getSource().getLabel()).append(RESET).append(']');
        if (!record.getLogger().isEmpty()) {
            result.append(' ').append(LOGGER).append(record.getLogger()).append(RESET);
        }
        result.append(" - ").append(highlightException(record.getMessage()));
        List<String> lines = record.getRawLines();
        for (int i = 1; i < lines.size(); i++) {
            result.append(System.lineSeparator()).append(continuation(lines.get(i)));
        }
        return result.toString();
    }

    private String renderRaw(ParsedRecord record) {
        List<String> lines = record.getRawLines();
        if (lines.isEmpty()) return "";
        String first = lines.get(0);
        first = replaceFirst(first, record.getTimestampText(), DIM, RESET);
        first = replaceFirst(first, record.getLevel(), levelCode(record.getLevel()), RESET);
        first = replaceFirst(first, record.getLogger(), LOGGER, RESET);
        StringBuilder result = new StringBuilder(highlightException(first));
        for (int i = 1; i < lines.size(); i++) {
            result.append(System.lineSeparator()).append(continuation(lines.get(i)));
        }
        return result.toString();
    }

    private static String continuation(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("at ") || trimmed.startsWith("... ")) return DIM + line + RESET;
        if (trimmed.startsWith("Caused by:") || trimmed.startsWith("Suppressed:")) {
            return ERROR + line + RESET;
        }
        return highlightException(line);
    }

    private static String highlightException(String value) {
        Matcher matcher = EXCEPTION.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) matcher.appendReplacement(result, Matcher.quoteReplacement(ERROR + matcher.group(1) + RESET));
        matcher.appendTail(result);
        return result.toString();
    }

    private static String replaceFirst(String value, String token, String prefix, String suffix) {
        if (token == null || token.isEmpty()) return value;
        int at = value.indexOf(token);
        if (at < 0) return value;
        return value.substring(0, at) + prefix + token + suffix + value.substring(at + token.length());
    }

    private static String level(String value) { return levelCode(value) + value + RESET; }

    private static String levelCode(String value) {
        if ("ERROR".equalsIgnoreCase(value)) return ERROR;
        if ("WARN".equalsIgnoreCase(value)) return "\033[33m";
        if ("DEBUG".equalsIgnoreCase(value)) return "\033[36m";
        if ("INFO".equalsIgnoreCase(value)) return "\033[32m";
        return SOURCE;
    }

    private static String join(List<String> lines) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) result.append(System.lineSeparator());
            result.append(lines.get(i));
        }
        return result.toString();
    }
}
