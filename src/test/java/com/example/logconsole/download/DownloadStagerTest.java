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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DownloadStagerTest {
    @TempDir Path temp;

    @Test void stagesInRangesAndReusesCompatibleCheckpoint() throws Exception {
        String content = "2026-07-13 10:00:00,000 [ACTIVE] INFO demo.Live - a sufficiently long fixture record\n";
        try (HttpFixture fixture = new HttpFixture(content);
             AuthContext auth = new AuthContext("user", "pass".toCharArray())) {
            AppConfig config = TestFixtures.config(fixture.baseUrl());
            AppConfig.ConnectionConfig connection = config.connections.get("main");
            ExpandedSource source = new ExpandedSource("example", "Example", "test", "a",
                    new LinkedHashMap<String, String>(), "test/a", new URL(fixture.baseUrl() + "test.log"),
                    connection, config.parsers.get("standard"), "UTC");
            AppConfig.DownloadConfig settings = new AppConfig.DownloadConfig();
            settings.rangeChunkBytes = 16;
            DownloadStager stager = new DownloadStager(new RangeHttpClient(auth, false), settings);
            List<StagedSource> first = stager.stage(Arrays.asList(source), temp, null);
            assertArrayEquals(fixture.content, Files.readAllBytes(first.get(0).getPath()));
            int requests = fixture.rangeGets.get();
            List<StagedSource> resumed = stager.stage(Arrays.asList(source), temp, null);
            assertEquals(requests, fixture.rangeGets.get());
            assertArrayEquals(fixture.content, Files.readAllBytes(resumed.get(0).getPath()));
        }
    }
}
