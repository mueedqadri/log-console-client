package com.example.logconsole.stream;

import com.example.logconsole.config.AppConfig;
import com.example.logconsole.model.ExpandedSource;
import com.example.logconsole.model.ParsedRecord;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogParser {
    private final ExpandedSource source;
    private final AppConfig.ParserConfig config;
    private final Pattern header;
    private final DateTimeFormatter timestampFormatter;
    private final ZoneId fallbackZone;

    public LogParser(ExpandedSource source) {
        this.source = source;
        this.config = source.getParser();
        this.header = Pattern.compile(config.headerRegex);
        this.timestampFormatter = DateTimeFormatter.ofPattern(config.timestampPattern);
        this.fallbackZone = ZoneId.of(source.getTimezone());
    }

    public List<ParsedRecord> parse(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        List<ParsedRecord> result = new ArrayList<ParsedRecord>();
        Mutable current = null;
        long sequence = 0;
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            if (line.isEmpty() && lineIndex == lines.length - 1) continue;
            Matcher matcher = header.matcher(line);
            if (matcher.matches()) {
                if (current != null) result.add(current.freeze(source));
                current = fromMatch(matcher, line, sequence++);
            } else if (current == null) {
                current = new Mutable(Instant.MIN, "", "UNKNOWN", "", line, sequence++, true);
            } else {
                current.lines.add(line);
            }
        }
        if (current != null) result.add(current.freeze(source));
        return result;
    }

    public Header tryHeader(String line, long sequence) {
        Matcher matcher = header.matcher(line);
        if (!matcher.matches()) return null;
        Mutable value = fromMatch(matcher, line, sequence);
        return new Header(value.timestamp, value.timestampText, value.level, value.logger, value.message);
    }

    private Mutable fromMatch(Matcher matcher, String raw, long sequence) {
        String timestampText = group(matcher, config.timestampGroup);
        Instant timestamp = parseTimestamp(timestampText);
        return new Mutable(timestamp, timestampText, group(matcher, config.levelGroup),
                group(matcher, config.loggerGroup), group(matcher, config.messageGroup), sequence, false, raw);
    }

    private Instant parseTimestamp(String value) {
        TemporalAccessor parsed = timestampFormatter.parseBest(value, ZonedDateTime::from,
                OffsetDateTime::from, LocalDateTime::from);
        if (parsed instanceof ZonedDateTime) return ((ZonedDateTime) parsed).toInstant();
        if (parsed instanceof OffsetDateTime) return ((OffsetDateTime) parsed).toInstant();
        return ((LocalDateTime) parsed).atZone(fallbackZone).toInstant();
    }

    private static String group(Matcher matcher, String name) {
        if (name == null) return "";
        String value = matcher.group(name);
        return value == null ? "" : value;
    }

    public static final class Header {
        public final Instant timestamp;
        public final String timestampText;
        public final String level;
        public final String logger;
        public final String message;

        Header(Instant timestamp, String timestampText, String level, String logger, String message) {
            this.timestamp = timestamp;
            this.timestampText = timestampText;
            this.level = level;
            this.logger = logger;
            this.message = message;
        }
    }

    private static final class Mutable {
        final Instant timestamp;
        final String timestampText;
        final String level;
        final String logger;
        final String message;
        final long sequence;
        final boolean leading;
        final List<String> lines = new ArrayList<String>();

        Mutable(Instant timestamp, String timestampText, String level, String logger, String message,
                long sequence, boolean leading, String raw) {
            this.timestamp = timestamp; this.timestampText = timestampText; this.level = level;
            this.logger = logger; this.message = message; this.sequence = sequence; this.leading = leading;
            this.lines.add(raw);
        }

        Mutable(Instant timestamp, String timestampText, String level, String logger, String raw,
                long sequence, boolean leading) {
            this(timestamp, timestampText, level, logger, raw, sequence, leading, raw);
        }

        ParsedRecord freeze(ExpandedSource source) {
            return new ParsedRecord(timestamp, timestampText, level, logger, message, lines, source, sequence, leading);
        }
    }
}
