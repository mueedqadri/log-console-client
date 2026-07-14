package com.example.logconsole.download;

import com.example.logconsole.model.ParsedRecord;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

final class PartWriter implements Closeable {
    private final Path directory;
    private final String baseName;
    private final long maxBytes;
    private final boolean overwrite;
    private final List<Path> temporary = new ArrayList<Path>();
    private long currentBytes;
    private OutputStream output;
    private int part;

    PartWriter(Path directory, String baseName, long maxBytes, boolean overwrite) throws IOException {
        this.directory = directory;
        this.baseName = baseName;
        this.maxBytes = maxBytes;
        this.overwrite = overwrite;
        Files.createDirectories(directory);
    }

    void write(ParsedRecord record) throws IOException {
        byte[] bytes = (record.renderCompact() + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxBytes) throw new IOException("A single logical record exceeds maxOutputBytes: " + record.getSource().getLabel());
        if (output == null || currentBytes + bytes.length > maxBytes) rotate();
        output.write(bytes);
        currentBytes += bytes.length;
    }

    List<Path> finish() throws IOException {
        if (temporary.isEmpty()) rotate();
        closeOutput();
        List<Path> published = new ArrayList<Path>();
        boolean multipart = temporary.size() > 1;
        for (int i = 0; i < temporary.size(); i++) {
            String finalName = multipart ? insertSuffix(baseName, ".part-" + String.format("%03d", i + 1)) : baseName;
            Path target = directory.resolve(finalName);
            if (Files.exists(target) && !overwrite) throw new IOException("Output already exists: " + target);
            move(temporary.get(i), target, overwrite);
            published.add(target);
        }
        temporary.clear();
        return published;
    }

    private void rotate() throws IOException {
        closeOutput();
        part++;
        Path path = directory.resolve("." + baseName + ".part-" + String.format("%03d", part) + ".tmp");
        Files.deleteIfExists(path);
        output = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW);
        temporary.add(path);
        currentBytes = 0;
    }

    private void closeOutput() throws IOException {
        if (output != null) { output.close(); output = null; }
    }

    @Override public void close() throws IOException { closeOutput(); }

    static String insertSuffix(String name, String suffix) {
        int dot = name.toLowerCase().lastIndexOf(".log");
        return dot < 0 ? name + suffix : name.substring(0, dot) + suffix + name.substring(dot);
    }

    private static void move(Path from, Path to, boolean overwrite) throws IOException {
        try {
            if (overwrite) Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            if (overwrite) Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(from, to);
        }
    }
}
