package com.example.logconsole.http;

import com.example.logconsole.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RangeHttpClientTest {
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
            RangeResponse response = client.getRange(url, config, 0, 5, metadata.getEtag(), true);
            assertArrayEquals(java.util.Arrays.copyOfRange(fixture.content, 0, 6), response.getBytes());
        }
    }

    @Test void rejectsIgnoredRangesAndBadCredentials() throws Exception {
        try (HttpFixture fixture = new HttpFixture("content");
             AuthContext auth = new AuthContext("user", "pass".toCharArray())) {
            RangeHttpClient client = new RangeHttpClient(auth, false);
            AppConfig.ConnectionConfig config = connection();
            assertThrows(IOException.class, () -> client.getRange(new URL(fixture.baseUrl() + "ignore-range.log"),
                    config, 1, 2, null, true));
        }
        try (HttpFixture fixture = new HttpFixture("content");
             AuthContext auth = new AuthContext("bad", "creds".toCharArray())) {
            RemoteMetadata metadata = new RangeHttpClient(auth, false).metadata(
                    new URL(fixture.baseUrl() + "test.log"), connection());
            assertEquals(401, metadata.getStatusCode());
        }
    }

    @Test void retriesWithoutIfRangeWhenTheServerReturns200ForTheSameStrongEtag() throws Exception {
        try (HttpFixture fixture = new HttpFixture("content");
             AuthContext auth = new AuthContext("user", "pass".toCharArray())) {
            fixture.rejectConditionalRanges = true;
            RangeHttpClient client = new RangeHttpClient(auth, false);
            URL url = new URL(fixture.baseUrl() + "test.log");

            RemoteMetadata metadata = client.metadata(url, connection());
            RangeResponse response = client.getRange(url, connection(), 1, 3, metadata.getEtag(), true);

            assertArrayEquals("ont".getBytes(StandardCharsets.UTF_8), response.getBytes());
            assertEquals(1, fixture.ifRangeGets.get());
            assertEquals(2, fixture.rangeGets.get());
        }
    }

    @Test void doesNotSendAWeakEtagAsIfRange() throws Exception {
        try (HttpFixture fixture = new HttpFixture("content");
             AuthContext auth = new AuthContext("user", "pass".toCharArray())) {
            fixture.etag = "W/\"v1\"";
            fixture.honorIfRange = true;
            RangeHttpClient client = new RangeHttpClient(auth, false);
            URL url = new URL(fixture.baseUrl() + "test.log");

            RemoteMetadata metadata = client.metadata(url, connection());
            RangeResponse response = client.getRange(url, connection(), 1, 3, metadata.getEtag(), true);

            assertArrayEquals("ont".getBytes(StandardCharsets.UTF_8), response.getBytes());
            assertEquals(0, fixture.ifRangeGets.get());
        }
    }

    @Test void reportsAnIfRangeMismatchInsteadOfCallingItAnIgnoredRange() throws Exception {
        try (HttpFixture fixture = new HttpFixture("content");
             AuthContext auth = new AuthContext("user", "pass".toCharArray())) {
            fixture.honorIfRange = true;
            RangeHttpClient client = new RangeHttpClient(auth, false);
            URL url = new URL(fixture.baseUrl() + "test.log");

            RemoteMetadata metadata = client.metadata(url, connection());
            fixture.append(" updated");
            IOException error = assertThrows(IOException.class,
                    () -> client.getRange(url, connection(), 1, 3, metadata.getEtag(), true));

            assertTrue(error.getMessage().contains("If-Range validator did not match"));
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

    static AppConfig.ConnectionConfig connection() {
        AppConfig.ConnectionConfig config = new AppConfig.ConnectionConfig();
        config.connectTimeoutMillis = 1000;
        config.readTimeoutMillis = 1000;
        config.retries = 0;
        return config;
    }
}
