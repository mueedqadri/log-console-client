package com.example.logconsole.download;

import com.example.logconsole.config.AppConfig;
import com.example.logconsole.http.RangeHttpClient;
import com.example.logconsole.http.RangeResponse;
import com.example.logconsole.http.RemoteMetadata;
import com.example.logconsole.model.ExpandedSource;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public final class DownloadStager {
    public interface Progress {
        void update(ExpandedSource source, long completed, long total);
    }

    private final RangeHttpClient client;
    private final AppConfig.DownloadConfig settings;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public DownloadStager(RangeHttpClient client, AppConfig.DownloadConfig settings) {
        this.client = client;
        this.settings = settings;
    }

    public List<StagedSource> stage(List<ExpandedSource> sources, Path jobDirectory, Progress progress) throws IOException {
        Files.createDirectories(jobDirectory);
        List<StagedSource> result = new ArrayList<StagedSource>();
        for (ExpandedSource source : sources) result.add(stageOne(source, jobDirectory, progress));
        return result;
    }

    private StagedSource stageOne(ExpandedSource source, Path jobDirectory, Progress progress) throws IOException {
        RemoteMetadata metadata = client.metadata(source.getUrl(), source.getConnection());
        if (!metadata.isAvailable()) throw new IOException("HTTP " + metadata.getStatusCode() + " for " + source.getUrl());
        if (metadata.getLength() < 0) throw new IOException("Missing Content-Length for " + source.getUrl());
        String id = digest(source.getUrl().toExternalForm());
        Path part = jobDirectory.resolve(id + ".part");
        Path checkpointPath = jobDirectory.resolve(id + ".checkpoint.json");
        DownloadCheckpoint checkpoint = readCheckpoint(checkpointPath);
        long existing = Files.isRegularFile(part) ? Files.size(part) : 0;
        boolean compatible = checkpoint != null
                && source.getUrl().toExternalForm().equals(checkpoint.url)
                && metadata.getLength() == checkpoint.expectedLength
                && metadata.identity().equals(checkpoint.identity)
                && existing == checkpoint.completedBytes;
        if (!compatible) {
            Files.deleteIfExists(part);
            Files.deleteIfExists(checkpointPath);
            existing = 0;
            checkpoint = fresh(source, metadata);
            writeCheckpoint(checkpointPath, checkpoint);
        }
        if (existing > metadata.getLength()) throw new IOException("Staging file exceeds expected source length");
        long offset = existing;
        if (progress != null) progress.update(source, offset, metadata.getLength());
        try (OutputStream output = Files.newOutputStream(part, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            while (offset < metadata.getLength()) {
                long end = Math.min(metadata.getLength() - 1, offset + settings.rangeChunkBytes - 1L);
                RangeResponse response = client.getRange(source.getUrl(), source.getConnection(), offset, end,
                        metadata.getEtag(), true);
                if (response.getBytes().length == 0) throw new IOException("Empty range response before end of source");
                output.write(response.getBytes());
                output.flush();
                offset += response.getBytes().length;
                checkpoint.completedBytes = offset;
                writeCheckpoint(checkpointPath, checkpoint);
                if (progress != null) progress.update(source, offset, metadata.getLength());
            }
        }
        return new StagedSource(source, part, metadata);
    }

    private DownloadCheckpoint readCheckpoint(Path path) {
        if (!Files.isRegularFile(path)) return null;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, DownloadCheckpoint.class);
        } catch (IOException | JsonParseException ignored) { return null; }
    }

    private void writeCheckpoint(Path path, DownloadCheckpoint checkpoint) throws IOException {
        Path temporary = path.resolveSibling(path.getFileName().toString() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            gson.toJson(checkpoint, writer);
        }
        Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static DownloadCheckpoint fresh(ExpandedSource source, RemoteMetadata metadata) {
        DownloadCheckpoint checkpoint = new DownloadCheckpoint();
        checkpoint.url = source.getUrl().toExternalForm();
        checkpoint.expectedLength = metadata.getLength();
        checkpoint.identity = metadata.identity();
        checkpoint.etag = metadata.getEtag();
        checkpoint.lastModified = metadata.getLastModified();
        checkpoint.completedBytes = 0;
        return checkpoint;
    }

    private static String digest(String value) throws IOException {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < 12; i++) result.append(String.format("%02x", bytes[i]));
            return result.toString();
        } catch (Exception e) { throw new IOException("Cannot create staging id", e); }
    }
}
