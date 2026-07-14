package com.example.logconsole.download;

import com.example.logconsole.model.ParsedRecord;
import com.example.logconsole.stream.RecordFilter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public final class ChronologicalMerger {
    public List<Path> merge(List<StagedSource> sources, Path outputDirectory, String fileName,
                            RecordFilter filter, long maxBytes, boolean overwrite) throws IOException {
        final List<FileRecordReader> readers = new ArrayList<FileRecordReader>();
        PriorityQueue<Entry> queue = new PriorityQueue<Entry>(11, new Comparator<Entry>() {
            @Override public int compare(Entry left, Entry right) {
                int timestamp = left.record.getTimestamp().compareTo(right.record.getTimestamp());
                if (timestamp != 0) return timestamp;
                int source = left.record.getSource().stableKey().compareTo(right.record.getSource().stableKey());
                if (source != 0) return source;
                return Long.compare(left.record.getSourceSequence(), right.record.getSourceSequence());
            }
        });
        PartWriter writer = new PartWriter(outputDirectory, fileName, maxBytes, overwrite);
        boolean finished = false;
        try {
            for (int i = 0; i < sources.size(); i++) {
                FileRecordReader reader = new FileRecordReader(sources.get(i), filter);
                readers.add(reader);
                ParsedRecord record = reader.next();
                if (record != null) queue.add(new Entry(i, record));
            }
            while (!queue.isEmpty()) {
                Entry entry = queue.remove();
                writer.write(entry.record);
                ParsedRecord next = readers.get(entry.reader).next();
                if (next != null) queue.add(new Entry(entry.reader, next));
            }
            List<Path> paths = writer.finish();
            finished = true;
            return paths;
        } finally {
            if (!finished) writer.close();
            for (FileRecordReader reader : readers) try { reader.close(); } catch (IOException ignored) { }
        }
    }

    private static final class Entry {
        final int reader;
        final ParsedRecord record;
        Entry(int reader, ParsedRecord record) { this.reader = reader; this.record = record; }
    }
}
