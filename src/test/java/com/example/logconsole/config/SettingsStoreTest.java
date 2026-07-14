package com.example.logconsole.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SettingsStoreTest {
    @TempDir Path temp;

    @Test void persistsOnlyUsernames() throws Exception {
        Path path = temp.resolve("settings.json");
        SettingsStore store = new SettingsStore(path);
        UserSettings settings = new UserSettings();
        settings.usernames.put("main", "remembered-user");
        store.save(settings);
        assertEquals("remembered-user", store.load().usernames.get("main"));
        String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8).toLowerCase();
        assertFalse(json.contains("password"));
        assertFalse(json.contains("authorization"));
    }
}
