package com.example.logconsole.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigLoaderTest {
    @TempDir Path temp;

    @Test void loadsBundledConfiguration() throws Exception {
        AppConfig config = new ConfigLoader().load(Paths.get("config", "log-console.json"));
        assertEquals(2, config.applications.size());
        assertEquals(10, new SourceExpander(config).expandCurrent("sample-service").size()
                + new SourceExpander(config).expandCurrent("batch-job").size());
    }

    @Test void validatesArchiveDatePlaceholder() {
        AppConfig config = com.example.logconsole.TestFixtures.config("http://127.0.0.1:8080/");
        config.applications.get("example").archivePathTemplate = "archive.log";
        assertThrows(IllegalArgumentException.class, () -> new ConfigLoader().validate(config));
    }

    @Test void rejectsUnknownNestedConfigurationProperties() throws Exception {
        String json = new String(Files.readAllBytes(Paths.get("config", "log-console.json")), StandardCharsets.UTF_8)
                .replace("\"baseUrl\":", "\"unexpected\": true, \"baseUrl\":");
        Path config = temp.resolve("config.json");
        Files.write(config, json.getBytes(StandardCharsets.UTF_8));

        assertThrows(IOException.class, () -> new ConfigLoader().load(config));
    }
}
