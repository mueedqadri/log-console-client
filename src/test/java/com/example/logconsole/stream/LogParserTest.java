package com.example.logconsole.stream;

import com.example.logconsole.TestFixtures;
import com.example.logconsole.model.ExpandedSource;
import com.example.logconsole.model.ParsedRecord;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogParserTest {
    @Test void attachesStackTraceAndRendersSourceInCompactMode() throws Exception {
        ExpandedSource source = TestFixtures.source("test/a/01/01");
        String text = "2026-07-13 10:00:00,000 [ACTIVE] ERROR demo.Worker - Failed\n"
                + "java.lang.IllegalStateException: boom\n\tat demo.Worker.run(Worker.java:1)\n"
                + "2026-07-13 10:00:01,000 [ACTIVE] INFO demo.Worker - Recovered\n";
        List<ParsedRecord> records = new LogParser(source).parse(text);
        assertEquals(2, records.size());
        assertEquals(3, records.get(0).getRawLines().size());
        assertTrue(records.get(0).renderCompact().contains("[test/a/01/01]"));
        assertTrue(records.get(0).renderCompact().contains("IllegalStateException"));
    }

    @Test void filtersWholeRecordsByLevelAndContinuationText() throws Exception {
        ExpandedSource source = TestFixtures.source("source");
        List<ParsedRecord> records = new LogParser(source).parse(
                "2026-07-13 10:00:00,000 [ACTIVE] ERROR demo.Worker - Failed\ncaused by needle\n"
                        + "2026-07-13 10:00:01,000 [ACTIVE] INFO demo.Worker - Fine\n");
        RecordFilter filter = new RecordFilter(new HashSet<String>(Arrays.asList("ERROR")), "needle");
        assertTrue(filter.test(records.get(0)));
        assertFalse(filter.test(records.get(1)));
    }

    @Test void preservesLeadingUnmatchedContent() throws Exception {
        List<ParsedRecord> records = new LogParser(TestFixtures.source("source")).parse(
                "orphan line\n2026-07-13 10:00:00,000 [ACTIVE] INFO demo.Worker - Fine\n");
        assertTrue(records.get(0).isLeadingUnmatched());
    }
}
