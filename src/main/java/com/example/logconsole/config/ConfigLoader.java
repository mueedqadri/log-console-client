package com.example.logconsole.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.regex.Pattern;

public final class ConfigLoader {
    private final Gson gson = new Gson();

    public AppConfig load(Path path) throws IOException {
        AppConfig config;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement document = JsonParser.parseReader(reader);
            rejectUnknownProperties(document, AppConfig.class, "$");
            config = gson.fromJson(document, AppConfig.class);
        } catch (JsonParseException e) {
            throw new IOException("Invalid JSON configuration: " + path, e);
        }
        validate(config);
        return config;
    }

    /** Gson ignores unknown fields, so retain the strict configuration contract Jackson provided. */
    private static void rejectUnknownProperties(JsonElement value, Type type, String path) throws IOException {
        if (value == null || value.isJsonNull()) return;
        Class<?> rawType = rawType(type);
        if (Map.class.isAssignableFrom(rawType)) {
            if (!value.isJsonObject()) return;
            Type valueType = typeArgument(type, 1);
            for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                rejectUnknownProperties(entry.getValue(), valueType, path + "." + entry.getKey());
            }
            return;
        }
        if (Iterable.class.isAssignableFrom(rawType)) {
            if (!value.isJsonArray()) return;
            Type itemType = typeArgument(type, 0);
            int index = 0;
            for (JsonElement item : value.getAsJsonArray()) {
                rejectUnknownProperties(item, itemType, path + "[" + index++ + "]");
            }
            return;
        }
        if (!isConfigurationType(rawType) || !value.isJsonObject()) return;

        Map<String, Field> fields = new LinkedHashMap<String, Field>();
        for (Field field : rawType.getFields()) {
            if (!Modifier.isStatic(field.getModifiers())) fields.put(field.getName(), field);
        }
        JsonObject object = value.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            Field field = fields.get(entry.getKey());
            if (field == null) throw new IOException("Unknown configuration property: " + path + "." + entry.getKey());
            rejectUnknownProperties(entry.getValue(), field.getGenericType(), path + "." + entry.getKey());
        }
    }

    private static boolean isConfigurationType(Class<?> type) {
        return type == AppConfig.class || type.getEnclosingClass() == AppConfig.class;
    }

    private static Class<?> rawType(Type type) {
        if (type instanceof Class<?>) return (Class<?>) type;
        if (type instanceof ParameterizedType) {
            Type raw = ((ParameterizedType) type).getRawType();
            if (raw instanceof Class<?>) return (Class<?>) raw;
        }
        return Object.class;
    }

    private static Type typeArgument(Type type, int index) {
        if (type instanceof ParameterizedType) {
            Type[] arguments = ((ParameterizedType) type).getActualTypeArguments();
            if (index < arguments.length) return arguments[index];
        }
        return Object.class;
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
