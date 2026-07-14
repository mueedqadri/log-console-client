package com.example.logconsole.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloadProgressDisplayTest {
    @Test void formatsStableAsciiProgressWithinTerminalWidth() {
        String line = DownloadProgressDisplay.formatLine(1, 8, "demo/location-a/cluster-01/log-01",
                5 * 1024 * 1024L, 10 * 1024 * 1024L, 80, '#', '-');

        assertTrue(line.contains("[##########----------]"));
        assertTrue(line.contains("50.0%"));
        assertTrue(line.contains("5.00 MiB"));
        assertEquals(80, line.length());
    }

    @Test void shrinksForNarrowTerminalsAndHandlesUnknownLength() {
        String narrow = DownloadProgressDisplay.formatLine(1, 2, "a-very-long-source-label",
                50, 100, 40, '#', '-');
        String unknown = DownloadProgressDisplay.formatLine(2, 2, "source", 4096, -1, 40, '#', '-');

        assertTrue(narrow.length() <= 40);
        assertTrue(narrow.contains("50.0%"));
        assertTrue(unknown.contains("4.00 KiB downloaded"));
        assertFalse(unknown.contains("%"));
    }

    @Test void acceptsUnicodeBarCharacters() {
        String line = DownloadProgressDisplay.formatLine(1, 1, "source", 1, 2, 70, '█', '░');
        assertTrue(line.contains("█"));
        assertTrue(line.contains("░"));
    }
}
