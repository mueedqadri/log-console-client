package com.example.logconsole.ui;

import com.example.logconsole.TestFixtures;
import com.example.logconsole.model.ParsedRecord;
import com.example.logconsole.stream.LogParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogHighlighterTest {
    @Test void highlightsStructuredTokensAndStackTrace() throws Exception {
        ParsedRecord record = new LogParser(TestFixtures.source("test/a")).parse(
                "2026-07-13 10:00:00,000 [ACTIVE] ERROR demo.Worker - Failed\n"
                        + "java.lang.IllegalStateException: boom\n\tat demo.Worker.run(Worker.java:1)\n").get(0);

        String rendered = new LogHighlighter(true).render(record, true);

        assertTrue(rendered.contains("\033[90m2026-07-13"));
        assertTrue(rendered.contains("\033[1;31mERROR\033[0m"));
        assertTrue(rendered.contains("\033[35mtest/a\033[0m"));
        assertTrue(rendered.contains("\033[90m\tat demo.Worker.run"));
    }

    @Test void noColorPreservesExistingCompactAndRawRendering() throws Exception {
        ParsedRecord record = new LogParser(TestFixtures.source("source")).parse(
                "2026-07-13 10:00:00,000 [ACTIVE] INFO demo.Worker - Fine\n").get(0);
        LogHighlighter highlighter = new LogHighlighter(false);

        assertEquals(record.renderCompact(), highlighter.render(record, true));
        assertEquals(record.renderRaw(), highlighter.render(record, false));
    }
}
