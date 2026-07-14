package com.example.logconsole.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ParsedRecord {
    private final Instant timestamp;
    private final String timestampText;
    private final String level;
    private final String logger;
    private final String message;
    private final List<String> rawLines;
    private final ExpandedSource source;
    private final long sourceSequence;
    private final boolean leadingUnmatched;

    public ParsedRecord(Instant timestamp, String timestampText, String level, String logger, String message,
                        List<String> rawLines, ExpandedSource source, long sourceSequence, boolean leadingUnmatched) {
        this.timestamp = timestamp;
        this.timestampText = timestampText;
        this.level = level == null ? "UNKNOWN" : level.toUpperCase();
        this.logger = logger == null ? "" : logger;
        this.message = message == null ? "" : message;
        this.rawLines = Collections.unmodifiableList(new ArrayList<String>(rawLines));
        this.source = source;
        this.sourceSequence = sourceSequence;
        this.leadingUnmatched = leadingUnmatched;
    }

    public Instant getTimestamp() { return timestamp; }
    public String getTimestampText() { return timestampText; }
    public String getLevel() { return level; }
    public String getLogger() { return logger; }
    public String getMessage() { return message; }
    public List<String> getRawLines() { return rawLines; }
    public ExpandedSource getSource() { return source; }
    public long getSourceSequence() { return sourceSequence; }
    public boolean isLeadingUnmatched() { return leadingUnmatched; }

    public String searchableText() {
        StringBuilder text = new StringBuilder(logger).append('\n').append(message);
        for (int i = 1; i < rawLines.size(); i++) text.append('\n').append(rawLines.get(i));
        return text.toString();
    }

    public String renderCompact() {
        if (leadingUnmatched) return "[UNMATCHED " + source.getLabel() + "] " + join(rawLines);
        StringBuilder result = new StringBuilder();
        result.append(timestampText).append(' ').append(level).append(" [").append(source.getLabel()).append("]");
        if (!logger.isEmpty()) result.append(' ').append(logger);
        result.append(" - ").append(message);
        for (int i = 1; i < rawLines.size(); i++) result.append(System.lineSeparator()).append(rawLines.get(i));
        return result.toString();
    }

    public String renderRaw() { return join(rawLines); }

    private static String join(List<String> lines) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) result.append(System.lineSeparator());
            result.append(lines.get(i));
        }
        return result.toString();
    }
}
