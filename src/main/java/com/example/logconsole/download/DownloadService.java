package com.example.logconsole.download;

import com.example.logconsole.config.AppConfig;
import com.example.logconsole.config.TemplateEngine;
import com.example.logconsole.config.ConfigResolver;
import com.example.logconsole.http.RangeHttpClient;
import com.example.logconsole.model.ExpandedSource;
import com.example.logconsole.stream.RecordFilter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DownloadService {
    private final AppConfig config;
    private final RangeHttpClient client;

    public DownloadService(AppConfig config, RangeHttpClient client) {
        this.config = config;
        this.client = client;
    }

    public List<Path> download(String applicationId, LocalDate date, List<ExpandedSource> selected,
                               RecordFilter filter, boolean overwrite, DownloadStager.Progress progress) throws IOException {
        if (selected == null || selected.isEmpty()) throw new IllegalArgumentException("At least one source must be selected");
        AppConfig.ApplicationConfig application = config.applications.get(applicationId);
        String environment = application.environment;
        AppConfig.DownloadConfig downloadConfig = ConfigResolver.download(config, application);
        Path outputDirectory = Paths.get(downloadConfig.root, safe(applicationId), safe(environment), date.toString()).normalize();
        Path staging = Paths.get(downloadConfig.stagingRoot,
                safe(applicationId + "-" + environment + "-" + date)).normalize();
        DownloadStager stager = new DownloadStager(client, downloadConfig);
        List<StagedSource> staged = stager.stage(selected, staging, progress);
        long rawTotal = 0;
        for (StagedSource value : staged) rawTotal += value.getMetadata().getLength();
        long maxBytes = application.maxOutputBytes == null ? downloadConfig.maxOutputBytes : application.maxOutputBytes;
        Map<String, List<StagedSource>> groups = groups(application, staged, rawTotal > maxBytes);
        Map<String, String> template = new LinkedHashMap<String, String>();
        template.put("application", application.application == null ? applicationId : application.application);
        template.put("application.id", applicationId);
        template.put("environment", environment);
        template.put("date", date.format(java.time.format.DateTimeFormatter.ofPattern(application.outputDatePattern)));
        String base = safeFileName(TemplateEngine.render(application.outputFileTemplate, template));
        if (filter != null) base = PartWriter.insertSuffix(base, ".filtered");
        Path assembledDirectory = staging.resolve("assembled");
        List<Path> assembled = new ArrayList<Path>();
        ChronologicalMerger merger = new ChronologicalMerger();
        for (Map.Entry<String, List<StagedSource>> group : groups.entrySet()) {
            String name = groups.size() == 1 && "all".equals(group.getKey())
                    ? base : PartWriter.insertSuffix(base, "." + safe(group.getKey()));
            assembled.addAll(merger.merge(group.getValue(), assembledDirectory, name, filter, maxBytes, true));
        }
        Files.createDirectories(outputDirectory);
        List<Path> targets = new ArrayList<Path>();
        for (Path source : assembled) {
            Path target = outputDirectory.resolve(source.getFileName().toString());
            if (Files.exists(target) && !overwrite) throw new IOException("Output already exists: " + target);
            targets.add(target);
        }
        List<Path> output = new ArrayList<Path>();
        for (int i = 0; i < assembled.size(); i++) {
            Path source = assembled.get(i);
            Path target = targets.get(i);
            try {
                if (overwrite) Files.move(source, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                else Files.move(source, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                if (overwrite) Files.move(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                else Files.move(source, target);
            }
            output.add(target);
        }
        deleteTree(staging);
        return output;
    }

    private static Map<String, List<StagedSource>> groups(AppConfig.ApplicationConfig app,
                                                           List<StagedSource> staged, boolean split) {
        Map<String, List<StagedSource>> result = new LinkedHashMap<String, List<StagedSource>>();
        if (!split) { result.put("all", staged); return result; }
        String dimension = app.splitGroupDimension;
        if (dimension == null && app.dimensions.containsKey("cluster")) dimension = "cluster";
        for (StagedSource source : staged) {
            String key = dimension == null ? source.getSource().getLocationId()
                    : source.getSource().getDimensions().get(dimension);
            if (key == null) throw new IllegalArgumentException("splitGroupDimension " + dimension + " is unavailable");
            List<StagedSource> values = result.get(key);
            if (values == null) { values = new ArrayList<StagedSource>(); result.put(key, values); }
            values.add(source);
        }
        return result;
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
