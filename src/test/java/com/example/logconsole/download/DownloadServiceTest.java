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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloadServiceTest {
    @TempDir Path temp;

    @Test void writesAllSourcesDirectlyToOneHeaderSeparatedFile() throws Exception {
        String content = "first log file";
        try (HttpFixture fixture = new HttpFixture(content);
             AuthContext auth = new AuthContext("user", "pass".toCharArray())) {
            AppConfig config = TestFixtures.config(fixture.baseUrl());
            config.defaults.download.root = temp.resolve("downloads").toString();
            AppConfig.ConnectionConfig connection = config.connections.get("main");
            connection.concurrency = 2;
            fixture.fullGetDelayMillis = 100;
            ExpandedSource first = source(fixture, connection, config, "location-a");
            ExpandedSource second = source(fixture, connection, config, "location-b");

            Path output = new DownloadService(config, new RangeHttpClient(auth, false)).download("example",
                    LocalDate.of(2026, 7, 14), Arrays.asList(first, second), null);

            String newline = System.lineSeparator();
            assertEquals("===== SOURCE: location-a =====" + newline + "first log file" + newline
                            + "===== SOURCE: location-b =====" + newline + "first log file",
                    new String(Files.readAllBytes(output), StandardCharsets.UTF_8));
            assertTrue(fixture.maxActiveFullGets.get() >= 2);
            assertEquals(2, fixture.fullGets.get());
            assertEquals(0, fixture.rangeGets.get());
        }
    }

    @Test void failedParallelJobLeavesExistingOutputUntouchedAndCleansParts() throws Exception {
        try (HttpFixture fixture = new HttpFixture("valid");
             AuthContext auth = new AuthContext("user", "pass".toCharArray())) {
            AppConfig config = TestFixtures.config(fixture.baseUrl());
            Path root = temp.resolve("downloads");
            config.defaults.download.root = root.toString();
            AppConfig.ConnectionConfig connection = config.connections.get("main");
            connection.concurrency = 2;
            ExpandedSource valid = source(fixture, connection, config, "valid");
            ExpandedSource missing = source(fixture, connection, config, "missing", "missing.log");
            Path output = root.resolve("example/test/2026-07-14/example.app.combined.2026-07-14.log");
            Files.createDirectories(output.getParent());
            Files.write(output, "previous".getBytes(StandardCharsets.UTF_8));

            assertThrows(java.io.IOException.class, () -> new DownloadService(config,
                    new RangeHttpClient(auth, false)).download("example", LocalDate.of(2026, 7, 14),
                    Arrays.asList(valid, missing), null));

            assertEquals("previous", new String(Files.readAllBytes(output), StandardCharsets.UTF_8));
            try (Stream<Path> children = Files.list(output.getParent())) {
                assertTrue(children.noneMatch(path -> path.getFileName().toString().contains(".download-")));
            }
        }
    }

    private static ExpandedSource source(HttpFixture fixture, AppConfig.ConnectionConfig connection,
                                         AppConfig config, String label) throws Exception {
        return source(fixture, connection, config, label, "test.log");
    }

    private static ExpandedSource source(HttpFixture fixture, AppConfig.ConnectionConfig connection,
                                         AppConfig config, String label, String path) throws Exception {
        return new ExpandedSource("example", "Example", "test", "a", new LinkedHashMap<String, String>(), label,
                new URL(fixture.baseUrl() + path), connection, config.parsers.get("standard"), "UTC");
    }
}
