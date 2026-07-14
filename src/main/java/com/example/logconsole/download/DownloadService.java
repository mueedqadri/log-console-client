package com.example.logconsole.download;

import com.example.logconsole.config.AppConfig;
import com.example.logconsole.config.TemplateEngine;
import com.example.logconsole.config.ConfigResolver;
import com.example.logconsole.http.RangeHttpClient;
import com.example.logconsole.model.ExpandedSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class DownloadService {
    private final AppConfig config;
    private final RangeHttpClient client;

    public DownloadService(AppConfig config, RangeHttpClient client) {
        this.config = config;
        this.client = client;
    }

    public List<Path> download(String applicationId, LocalDate date, List<ExpandedSource> selected,
                               DownloadStager.Progress progress) throws IOException {
        if (selected == null || selected.isEmpty()) throw new IllegalArgumentException("At least one source must be selected");
        AppConfig.ApplicationConfig application = config.applications.get(applicationId);
        String environment = application.environment;
        AppConfig.DownloadConfig downloadConfig = ConfigResolver.download(config, application);
        Path outputDirectory = Paths.get(downloadConfig.root, safe(applicationId), safe(environment), date.toString()).normalize();
        Path staging = Paths.get(downloadConfig.stagingRoot,
                safe(applicationId + "-" + environment + "-" + date)).normalize();
        DownloadStager stager = new DownloadStager(client, downloadConfig);
        List<StagedSource> staged = stager.stage(selected, staging, progress);
        java.util.Map<String, String> template = new java.util.LinkedHashMap<String, String>();
        template.put("application", application.application == null ? applicationId : application.application);
        template.put("application.id", applicationId);
        template.put("environment", environment);
        template.put("date", date.format(java.time.format.DateTimeFormatter.ofPattern(application.outputDatePattern)));
        String base = safeFileName(TemplateEngine.render(application.outputFileTemplate, template));
        Path assembledDirectory = staging.resolve("assembled");
        Path assembled = new SourceConcatenator().concatenate(staged, assembledDirectory, base, true);
        Files.createDirectories(outputDirectory);
        Path output = outputDirectory.resolve(assembled.getFileName().toString());
        move(assembled, output);
        deleteTree(staging);
        List<Path> published = new ArrayList<Path>();
        published.add(output);
        return published;
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
            Files.move(from, to, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(from, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        List<Path> paths = new ArrayList<Path>();
        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path path : stream) paths.add(path);
        }
        for (Path path : paths) {
            if (Files.isDirectory(path)) deleteTree(path); else Files.deleteIfExists(path);
        }
        Files.deleteIfExists(root);
    }
}
