package com.example.logconsole.download;

import com.example.logconsole.model.ExpandedSource;
import com.example.logconsole.model.ParsedRecord;
import com.example.logconsole.stream.LogParser;
import com.example.logconsole.stream.RecordFilter;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class FileRecordReader implements Closeable {
    private final ExpandedSource source;
    private final BufferedReader reader;
    private final LogParser parser;
    private final RecordFilter filter;
    private String pendingLine;
    private LogParser.Header pendingHeader;
    private long sequence;
    private Instant lastTimestamp;

    FileRecordReader(StagedSource staged, RecordFilter filter) throws IOException {
        this.source = staged.getSource();
        this.reader = Files.newBufferedReader(staged.getPath(), StandardCharsets.UTF_8);
        this.parser = new LogParser(source);
        this.filter = filter;
    }

    ParsedRecord next() throws IOException {
        while (true) {
            ParsedRecord record = nextUnfiltered();
            if (record == null || filter == null || filter.test(record)) return record;
        }
    }

    private ParsedRecord nextUnfiltered() throws IOException {
        String first = pendingLine;
        LogParser.Header header = pendingHeader;
        pendingLine = null;
        pendingHeader = null;
        if (first == null) {
            first = reader.readLine();
            if (first == null) return null;
            header = parser.tryHeader(first, sequence);
        }
        List<String> lines = new ArrayList<String>();
        lines.add(first);
        boolean leading = header == null;
        String line;
        while ((line = reader.readLine()) != null) {
            LogParser.Header candidate = parser.tryHeader(line, sequence + 1);
            if (candidate != null) {
                pendingLine = line;
                pendingHeader = candidate;
                break;
            }
            lines.add(line);
        }
        ParsedRecord record;
        if (leading) {
            record = new ParsedRecord(Instant.MIN, "", "UNKNOWN", "", first, lines, source, sequence++, true);
        } else {
            if (lastTimestamp != null && header.timestamp.isBefore(lastTimestamp)) {
                throw new IOException("Timestamp regression in " + source.getLabel() + " near record " + sequence);
            }
            lastTimestamp = header.timestamp;
            record = new ParsedRecord(header.timestamp, header.timestampText, header.level, header.logger,
                    header.message, lines, source, sequence++, false);
        }
        return record;
    }

    @Override public void close() throws IOException { reader.close(); }
}
