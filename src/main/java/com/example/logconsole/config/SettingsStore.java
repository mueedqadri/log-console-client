package com.example.logconsole.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public final class SettingsStore {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path path;

    public SettingsStore() {
        this(defaultPath());
    }

    SettingsStore(Path path) {
        this.path = path;
    }

    public UserSettings load() {
        if (!Files.isRegularFile(path)) return new UserSettings();
        try { return mapper.readValue(Files.newInputStream(path), UserSettings.class); }
        catch (IOException ignored) { return new UserSettings(); }
    }

    public void save(UserSettings settings) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName().toString() + ".tmp");
        mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), settings);
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
