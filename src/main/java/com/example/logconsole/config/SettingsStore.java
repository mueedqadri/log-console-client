package com.example.logconsole.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public final class SettingsStore {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;

    public SettingsStore() {
        this(defaultPath());
    }

    SettingsStore(Path path) {
        this.path = path;
    }

    public UserSettings load() {
        if (!Files.isRegularFile(path)) return new UserSettings();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, UserSettings.class);
        } catch (IOException | JsonParseException ignored) { return new UserSettings(); }
    }

    public void save(UserSettings settings) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName().toString() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            gson.toJson(settings, writer);
        }
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static Path defaultPath() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.trim().isEmpty()) return Paths.get(appData, "LogConsole", "settings.json");
        }
        return Paths.get(System.getProperty("user.home"), ".log-console", "settings.json");
    }
}
