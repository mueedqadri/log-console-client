package com.example.logconsole.stream;

import com.example.logconsole.config.AppConfig;
import com.example.logconsole.http.RangeHttpClient;
import com.example.logconsole.http.RangeResponse;
import com.example.logconsole.http.RemoteMetadata;
import com.example.logconsole.model.ExpandedSource;
import com.example.logconsole.model.ParsedRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class TailSession {
    public enum Change { NONE, APPENDED, ROTATED }

    private final ExpandedSource source;
    private final RangeHttpClient client;
    private final AppConfig.StreamConfig settings;
    private final LogParser parser;
    private long loadedStart;
    private long knownLength;
    private String identity;
    private String text = "";

    public TailSession(ExpandedSource source, RangeHttpClient client, AppConfig.StreamConfig settings) {
        this.source = source;
        this.client = client;
        this.settings = settings;
        this.parser = new LogParser(source);
    }

    public void open() throws IOException {
        RemoteMetadata metadata = client.metadata(source.getUrl(), source.getConnection());
        if (!metadata.isAvailable()) throw new IOException("HTTP " + metadata.getStatusCode() + " for " + source.getUrl());
        if (!metadata.isRangeSupported() && metadata.getLength() > 0) {
            // The first actual range request remains authoritative because some servers omit Accept-Ranges.
        }
        knownLength = Math.max(0, metadata.getLength());
        identity = metadata.identity();
        long desired = (long) settings.chunkBytes * Math.max(1, settings.initialChunks);
        loadedStart = Math.max(0, knownLength - desired);
        if (knownLength == 0) { text = ""; return; }
        RangeResponse response = client.getRange(source.getUrl(), source.getConnection(), loadedStart,
                knownLength - 1, true);
        text = new String(response.getBytes(), StandardCharsets.UTF_8);
        if (loadedStart > 0) discardLeadingPartialLine();
        trimLines();
    }

    public boolean loadOlder() throws IOException {
        if (loadedStart <= 0) return false;
        long start = Math.max(0, loadedStart - settings.chunkBytes);
        RangeResponse response = client.getRange(source.getUrl(), source.getConnection(), start,
                loadedStart - 1, true);
        String prefix = new String(response.getBytes(), StandardCharsets.UTF_8);
        if (start > 0) {
            int newline = prefix.indexOf('\n');
            if (newline >= 0) {
                int removed = prefix.substring(0, newline + 1).getBytes(StandardCharsets.UTF_8).length;
                start += removed;
                prefix = prefix.substring(newline + 1);
            } else {
                loadedStart = start;
                return true;
            }
        }
        text = prefix + text;
        loadedStart = start;
        trimLines();
        return true;
    }

    public Change poll() throws IOException {
        RemoteMetadata metadata = client.metadata(source.getUrl(), source.getConnection());
        if (!metadata.isAvailable()) throw new IOException("HTTP " + metadata.getStatusCode() + " while polling");
        if (metadata.getLength() < knownLength || (!identity.equals(metadata.identity()) && metadata.getLength() <= knownLength)) {
            open();
            return Change.ROTATED;
        }
        if (metadata.getLength() > knownLength) {
            RangeResponse response = client.getRange(source.getUrl(), source.getConnection(), knownLength,
                    metadata.getLength() - 1, true);
            text += new String(response.getBytes(), StandardCharsets.UTF_8);
            knownLength = metadata.getLength();
            identity = metadata.identity();
            trimLines();
            return Change.APPENDED;
        }
        identity = metadata.identity();
        return Change.NONE;
    }

    public List<ParsedRecord> records(RecordFilter filter) {
        List<ParsedRecord> parsed = parser.parse(text);
        List<ParsedRecord> filtered = new ArrayList<ParsedRecord>();
        for (ParsedRecord record : parsed) if (filter == null || filter.test(record)) filtered.add(record);
        return filtered;
    }

    public long getLoadedStart() { return loadedStart; }
    public long getKnownLength() { return knownLength; }

    private void discardLeadingPartialLine() {
        int newline = text.indexOf('\n');
        if (newline < 0) { loadedStart = knownLength; text = ""; return; }
        loadedStart += text.substring(0, newline + 1).getBytes(StandardCharsets.UTF_8).length;
        text = text.substring(newline + 1);
    }

    private void trimLines() {
        int lines = 0;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') lines++;
        int remove = lines - settings.maxBufferedLines;
        if (remove <= 0) return;
        int position = 0;
        while (remove > 0) {
            int newline = text.indexOf('\n', position);
            if (newline < 0) return;
            position = newline + 1;
            remove--;
        }
        loadedStart += text.substring(0, position).getBytes(StandardCharsets.UTF_8).length;
        text = text.substring(position);
    }
}
