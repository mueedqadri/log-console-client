package com.example.logconsole.http;

import com.example.logconsole.config.AppConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RangeHttpClientTest {
    @TempDir Path temp;

    @Test void readsMetadataAndAuthenticatedRanges() throws Exception {
        try (HttpFixture fixture = new HttpFixture("héllo\nworld\n");
             AuthContext auth = new AuthContext("user", "pass".toCharArray())) {
            RangeHttpClient client = new RangeHttpClient(auth, false);
            AppConfig.ConnectionConfig config = connection();
            URL url = new URL(fixture.baseUrl() + "test.log");
            RemoteMetadata metadata = client.metadata(url, config);
            assertTrue(metadata.isAvailable());
            assertTrue(metadata.isRangeSupported());
            assertEquals(fixture.content.length, metadata.getLength());
            RangeResponse response = client.getRange(url, config, 0, 5, true);
            assertArrayEquals(java.util.Arrays.copyOfRange(fixture.content, 0, 6), response.getBytes());
        }
    }

    @Test void rejectsIgnoredRangesAndBadCredentials() throws Exception {
        try (HttpFixture fixture = new HttpFixture("content");
             AuthContext auth = new AuthContext("user", "pass".toCharArray())) {
            RangeHttpClient client = new RangeHttpClient(auth, false);
            AppConfig.ConnectionConfig config = connection();
            assertThrows(IOException.class, () -> client.getRange(new URL(fixture.baseUrl() + "ignore-range.log"),
                    config, 1, 2, true));
        }
        try (HttpFixture fixture = new HttpFixture("content");
             AuthContext auth = new AuthContext("bad", "creds".toCharArray())) {
            RemoteMetadata metadata = new RangeHttpClient(auth, false).metadata(
                    new URL(fixture.baseUrl() + "test.log"), connection());
            assertEquals(401, metadata.getStatusCode());
        }
    }

    @Test void neverSendsIfRange() throws Exception {
        try (HttpFixture fixture = new HttpFixture("content");
             AuthContext auth = new AuthContext("user", "pass".toCharArray())) {
            RangeHttpClient client = new RangeHttpClient(auth, false);
            URL url = new URL(fixture.baseUrl() + "test.log");

            RemoteMetadata metadata = client.metadata(url, connection());
            RangeResponse response = client.getRange(url, connection(), 1, 3, true);

            assertArrayEquals("ont".getBytes(StandardCharsets.UTF_8), response.getBytes());
            assertTrue(metadata.getEtag() != null);
            assertEquals(0, fixture.ifRangeGets.get());
            assertEquals(2, fixture.rangeGets.get());
        }
    }

    @Test void probesMetadataWithOneByteRangeGetWithoutUsingHead() throws Exception {
        try (HttpFixture fixture = new HttpFixture("content");
             AuthContext auth = new AuthContext("user", "pass".toCharArray())) {
            RemoteMetadata metadata = new RangeHttpClient(auth, false).metadata(
                    new URL(fixture.baseUrl() + "head-unsupported.log"), connection());

            assertTrue(metadata.isAvailable());
            assertTrue(metadata.isRangeSupported());
            assertEquals(fixture.content.length, metadata.getLength());
            assertEquals(1, fixture.rangeGets.get());
        }
    }

    @Test void streamsNormalGetToDiskWithIncrementalProgress() throws Exception {
        char[] chars = new char[65536];
        java.util.Arrays.fill(chars, 'x');
        try (HttpFixture fixture = new HttpFixture(new String(chars));
             AuthContext auth = new AuthContext("user", "pass".toCharArray())) {
            final List<Long> updates = new ArrayList<Long>();
            Path output = temp.resolve("download.part");

            new RangeHttpClient(auth, false).downloadTo(new URL(fixture.baseUrl() + "test.log"), connection(),
                    output, new RangeHttpClient.TransferProgress() {
                        @Override public void update(long completed, long total) { updates.add(completed); }
                    });

            assertArrayEquals(fixture.content, Files.readAllBytes(output));
            assertTrue(updates.size() > 2);
            assertEquals(0L, updates.get(0).longValue());
            assertEquals(fixture.content.length, updates.get(updates.size() - 1).longValue());
            assertEquals(0, fixture.rangeGets.get());
            assertEquals(1, fixture.fullGets.get());
        }
    }

    @Test void retryRestartsNormalGetWithoutDuplicatingPartialBytes() throws Exception {
        try (HttpFixture fixture = new HttpFixture("complete-download-content");
             AuthContext auth = new AuthContext("user", "pass".toCharArray())) {
            AppConfig.ConnectionConfig config = connection();
            config.retries = 1;
            Path output = temp.resolve("retried.part");

            new RangeHttpClient(auth, false).downloadTo(new URL(fixture.baseUrl() + "flaky-download.log"),
                    config, output, null);

            assertArrayEquals(fixture.content, Files.readAllBytes(output));
            assertEquals(2, fixture.flakyGets.get());
        }
    }

    static AppConfig.ConnectionConfig connection() {
        AppConfig.ConnectionConfig config = new AppConfig.ConnectionConfig();
        config.connectTimeoutMillis = 1000;
        config.readTimeoutMillis = 1000;
        config.retries = 0;
        return config;
    }
}
