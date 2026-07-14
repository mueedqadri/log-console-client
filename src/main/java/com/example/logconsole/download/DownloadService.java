package com.example.logconsole.download;

import com.example.logconsole.config.AppConfig;
import com.example.logconsole.config.TemplateEngine;
import com.example.logconsole.config.ConfigResolver;
import com.example.logconsole.http.RangeHttpClient;
import com.example.logconsole.http.RangeResponse;
import com.example.logconsole.http.RemoteMetadata;
import com.example.logconsole.model.ExpandedSource;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.List;

public final class DownloadService {
    public interface Progress {
        void update(int sourceNumber, int sourceCount, ExpandedSource source, long completed, long total);
    }

    private final AppConfig config;
    private final RangeHttpClient client;

    public DownloadService(AppConfig config, RangeHttpClient client) {
        this.config = config;
        this.client = client;
    }

    public Path download(String applicationId, LocalDate date, List<ExpandedSource> selected, Progress progress)
            throws IOException {
        if (selected == null || selected.isEmpty()) throw new IllegalArgumentException("At least one source must be selected");
        AppConfig.ApplicationConfig application = config.applications.get(applicationId);
        String environment = application.environment;
        AppConfig.DownloadConfig downloadConfig = ConfigResolver.download(config, application);
        Path outputDirectory = Paths.get(downloadConfig.root, safe(applicationId), safe(environment), date.toString()).normalize();
        java.util.Map<String, String> template = new java.util.LinkedHashMap<String, String>();
        template.put("application", application.application == null ? applicationId : application.application);
        template.put("application.id", applicationId);
        template.put("environment", environment);
        template.put("date", date.format(java.time.format.DateTimeFormatter.ofPattern(application.outputDatePattern)));
        String fileName = safeFileName(TemplateEngine.render(application.outputFileTemplate, template));
        Files.createDirectories(outputDirectory);
        Path output = outputDirectory.resolve(fileName);
        Path temporary = outputDirectory.resolve("." + fileName + ".tmp");
        Files.deleteIfExists(temporary);
        boolean complete = false;
        try (OutputStream writer = Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW)) {
            for (int i = 0; i < selected.size(); i++) {
                ExpandedSource source = selected.get(i);
                if (i > 0) writer.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
                writer.write(("===== SOURCE: " + source.getLabel() + " =====" + System.lineSeparator())
                        .getBytes(StandardCharsets.UTF_8));
                downloadSource(writer, source, i + 1, selected.size(), downloadConfig.rangeChunkBytes, progress);
            }
            move(temporary, output);
            complete = true;
            return output;
        } finally {
            if (!complete) Files.deleteIfExists(temporary);
        }
    }

    private void downloadSource(OutputStream writer, ExpandedSource source, int sourceNumber, int sourceCount,
                                int chunkBytes, Progress progress) throws IOException {
        RemoteMetadata metadata = client.metadata(source.getUrl(), source.getConnection());
        if (!metadata.isAvailable()) throw new IOException("HTTP " + metadata.getStatusCode() + " for " + source.getUrl());
        if (metadata.getLength() < 0) throw new IOException("Missing Content-Length for " + source.getUrl());
        long completed = 0;
        notifyProgress(progress, sourceNumber, sourceCount, source, completed, metadata.getLength());
        while (completed < metadata.getLength()) {
            long end = Math.min(metadata.getLength() - 1, completed + chunkBytes - 1L);
            RangeResponse response = client.getRange(source.getUrl(), source.getConnection(), completed, end, true);
            if (response.getBytes().length == 0) throw new IOException("Empty range response before end of source");
            writer.write(response.getBytes());
            completed += response.getBytes().length;
            notifyProgress(progress, sourceNumber, sourceCount, source, completed, metadata.getLength());
        }
    }

    private static void notifyProgress(Progress progress, int sourceNumber, int sourceCount, ExpandedSource source,
                                       long completed, long total) {
        if (progress != null) progress.update(sourceNumber, sourceCount, source, completed, total);
    }

    private static String safe(String value) {
        String sanitized = value.replaceAll("[^A-Za-z0-9._-]", "_");
        if (sanitized.isEmpty() || ".".equals(sanitized) || "..".equals(sanitized)) throw new IllegalArgumentException("Unsafe path segment: " + value);
        return sanitized;
    }

    private static String safeFileName(String name) {
        if (!name.endsWith(".log") || !Paths.get(name).getFileName().toString().equals(name)
                || name.contains("..") || name.contains("/") || name.contains("\\")) {
            throw new IllegalArgumentException("Unsafe output filename: " + name);
        }
        return name;
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
