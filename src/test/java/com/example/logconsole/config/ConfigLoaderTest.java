package com.example.logconsole.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigLoaderTest {
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
}
