package com.example.logconsole.download;

import com.example.logconsole.TestFixtures;
import com.example.logconsole.config.AppConfig;
import com.example.logconsole.http.AuthContext;
import com.example.logconsole.http.HttpFixture;
import com.example.logconsole.http.RangeHttpClient;
import com.example.logconsole.model.ExpandedSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DownloadServiceTest {
    @TempDir Path temp;

    @Test void writesAllSourcesDirectlyToOneHeaderSeparatedFile() throws Exception {
        String content = "first log file";
        try (HttpFixture fixture = new HttpFixture(content);
             AuthContext auth = new AuthContext("user", "pass".toCharArray())) {
            AppConfig config = TestFixtures.config(fixture.baseUrl());
            config.defaults.download.root = temp.resolve("downloads").toString();
            config.defaults.download.rangeChunkBytes = 16;
            AppConfig.ConnectionConfig connection = config.connections.get("main");
            ExpandedSource first = source(fixture, connection, config, "location-a");
            ExpandedSource second = source(fixture, connection, config, "location-b");

            Path output = new DownloadService(config, new RangeHttpClient(auth, false)).download("example",
                    LocalDate.of(2026, 7, 14), Arrays.asList(first, second), null);

            String newline = System.lineSeparator();
            assertEquals("===== SOURCE: location-a =====" + newline + "first log file" + newline
                            + "===== SOURCE: location-b =====" + newline + "first log file",
                    new String(Files.readAllBytes(output), StandardCharsets.UTF_8));
        }
    }

    private static ExpandedSource source(HttpFixture fixture, AppConfig.ConnectionConfig connection,
                                         AppConfig config, String label) throws Exception {
        return new ExpandedSource("example", "Example", "test", "a", new LinkedHashMap<String, String>(), label,
                new URL(fixture.baseUrl() + "test.log"), connection, config.parsers.get("standard"), "UTC");
    }
}
