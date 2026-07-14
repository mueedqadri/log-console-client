package com.example.logconsole.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.regex.Pattern;

public final class ConfigLoader {
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public AppConfig load(Path path) throws IOException {
        AppConfig config = mapper.readValue(Files.newInputStream(path), AppConfig.class);
        validate(config);
        return config;
    }

    public void validate(AppConfig config) {
        if (config.schemaVersion != 1) throw new IllegalArgumentException("Unsupported schemaVersion: " + config.schemaVersion);
        if (config.connections.isEmpty() || config.environments.isEmpty() || config.locations.isEmpty()
                || config.parsers.isEmpty() || config.applications.isEmpty()) {
            throw new IllegalArgumentException("connections, environments, locations, parsers, and applications are required");
        }
        for (Map.Entry<String, AppConfig.ParserConfig> entry : config.parsers.entrySet()) {
            AppConfig.ParserConfig parser = entry.getValue();
            Pattern.compile(parser.headerRegex);
            DateTimeFormatter.ofPattern(parser.timestampPattern);
            if (parser.timezone != null) ZoneId.of(parser.timezone);
            requireNamedGroup(parser.headerRegex, parser.timestampGroup, entry.getKey());
            requireNamedGroup(parser.headerRegex, parser.levelGroup, entry.getKey());
            requireNamedGroup(parser.headerRegex, parser.loggerGroup, entry.getKey());
            requireNamedGroup(parser.headerRegex, parser.messageGroup, entry.getKey());
        }
        for (Map.Entry<String, AppConfig.EnvironmentConfig> entry : config.environments.entrySet()) {
            AppConfig.EnvironmentConfig env = entry.getValue();
            if (!config.connections.containsKey(env.connection)) throw new IllegalArgumentException("Environment " + entry.getKey() + " has unknown connection");
            ZoneId.of(env.timezone);
        }
        SourceExpander expander = new SourceExpander(config);
        for (Map.Entry<String, AppConfig.ApplicationConfig> entry : config.applications.entrySet()) {
            AppConfig.ApplicationConfig app = entry.getValue();
            if (app.archivePathTemplate == null || !app.archivePathTemplate.contains("{date}")) {
                throw new IllegalArgumentException("Application " + entry.getKey() + " archivePathTemplate must contain {date}");
            }
            DateTimeFormatter.ofPattern(app.archiveDatePattern);
            DateTimeFormatter.ofPattern(app.outputDatePattern);
            if (app.outputFileTemplate == null || !app.outputFileTemplate.endsWith(".log")) {
                throw new IllegalArgumentException("Application " + entry.getKey() + " outputFileTemplate must end with .log");
            }
            Map<String, String> outputValues = new LinkedHashMap<String, String>();
            outputValues.put("application", app.application == null ? entry.getKey() : app.application);
            outputValues.put("application.id", entry.getKey());
            outputValues.put("environment", app.environment);
            outputValues.put("date", java.time.LocalDate.of(2000, 1, 2).format(DateTimeFormatter.ofPattern(app.outputDatePattern)));
            String output = TemplateEngine.render(app.outputFileTemplate, outputValues);
            if (!java.nio.file.Paths.get(output).getFileName().toString().equals(output)
                    || output.contains("..") || output.contains("/") || output.contains("\\")) {
                throw new IllegalArgumentException("Application " + entry.getKey() + " has unsafe outputFileTemplate");
            }
            if (app.splitGroupDimension != null && !app.dimensions.containsKey(app.splitGroupDimension)) {
                throw new IllegalArgumentException("Application " + entry.getKey() + " has unknown splitGroupDimension");
            }
            expander.expandCurrent(entry.getKey());
            expander.expandArchive(entry.getKey(), java.time.LocalDate.of(2000, 1, 2));
        }
        validateThresholds(config.defaults.status);
        validateStream(config.defaults.streaming);
        validateDownload(config.defaults.download);
        for (AppConfig.EnvironmentConfig environment : config.environments.values()) {
            validateThresholds(environment.status);
            validateStream(environment.streaming);
            validateDownload(environment.download);
        }
        for (AppConfig.ApplicationConfig application : config.applications.values()) {
            validateThresholds(application.thresholds);
            validateStream(application.streaming);
            validateDownload(application.download);
        }
    }

    private static void requireNamedGroup(String regex, String group, String parserId) {
        if (group != null && !regex.contains("(?<" + group + ">")) {
            throw new IllegalArgumentException("Parser " + parserId + " regex is missing named group " + group);
        }
    }

    private static void validateThresholds(AppConfig.ThresholdsConfig value) {
        if (value != null && (value.warningBytes < 0 || value.criticalBytes <= value.warningBytes))
            throw new IllegalArgumentException("Override thresholds must satisfy 0 <= warning < critical");
    }

    private static void validateStream(AppConfig.StreamConfig value) {
        if (value != null && (value.chunkBytes < 1024 || value.initialChunks < 1
                || value.maxBufferedLines < 1 || value.pollMillis < 100))
            throw new IllegalArgumentException("Invalid streaming override");
    }

    private static void validateDownload(AppConfig.DownloadConfig value) {
        if (value != null && (value.rangeChunkBytes < 1024 || value.maxOutputBytes < 1024
                || value.root == null || value.stagingRoot == null))
            throw new IllegalArgumentException("Invalid download override");
    }
}
