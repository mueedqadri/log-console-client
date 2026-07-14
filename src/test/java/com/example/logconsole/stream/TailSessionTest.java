package com.example.logconsole.stream;

import com.example.logconsole.TestFixtures;
import com.example.logconsole.config.AppConfig;
import com.example.logconsole.http.AuthContext;
import com.example.logconsole.http.HttpFixture;
import com.example.logconsole.http.RangeHttpClient;
import com.example.logconsole.model.ExpandedSource;
import com.example.logconsole.model.ParsedRecord;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TailSessionTest {
    @Test void opensTailAndPollsAppendedRecord() throws Exception {
        String initial = "2026-07-13 10:00:00,000 [ACTIVE] INFO demo.Live - initial\n";
        try (HttpFixture fixture = new HttpFixture(initial);
             AuthContext auth = new AuthContext("user", "pass".toCharArray())) {
            AppConfig config = TestFixtures.config(fixture.baseUrl());
            AppConfig.ConnectionConfig connection = config.connections.get("main");
            ExpandedSource source = new ExpandedSource("example", "Example", "test", "a",
                    new LinkedHashMap<String, String>(), "test/a", new URL(fixture.baseUrl() + "test.log"),
                    connection, config.parsers.get("standard"), "UTC");
            AppConfig.StreamConfig settings = new AppConfig.StreamConfig();
            settings.chunkBytes = 1024;
            settings.initialChunks = 1;
            settings.maxBufferedLines = 100;
            TailSession session = new TailSession(source, new RangeHttpClient(auth, false), settings);
            session.open();
            assertEquals(1, session.records(null).size());
            fixture.append("2026-07-13 10:00:01,000 [ACTIVE] ERROR demo.Live - appended\nstack line\n");
            assertEquals(TailSession.Change.APPENDED, session.poll());
            List<ParsedRecord> records = session.records(null);
            assertEquals(2, records.size());
            assertTrue(records.get(1).renderCompact().contains("stack line"));
        }
    }
}
