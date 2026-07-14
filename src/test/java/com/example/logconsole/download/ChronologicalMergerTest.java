package com.example.logconsole.download;

import com.example.logconsole.TestFixtures;
import com.example.logconsole.http.RemoteMetadata;
import com.example.logconsole.model.ExpandedSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChronologicalMergerTest {
    @TempDir Path temp;

    @Test void mergesChronologicallyAndUsesStableSourceLabels() throws Exception {
        StagedSource a = staged("a", "2026-07-13 10:00:00,000 [ACTIVE] INFO demo.A - first\n"
                + "2026-07-13 10:00:02,000 [ACTIVE] INFO demo.A - third\n");
        StagedSource b = staged("b", "2026-07-13 10:00:01,000 [ACTIVE] WARN demo.B - second\n");
        List<Path> output = new ChronologicalMerger().merge(Arrays.asList(a, b), temp.resolve("out"),
                "combined.log", null, 1024 * 1024, false);
        assertEquals(1, output.size());
        String value = new String(Files.readAllBytes(output.get(0)), StandardCharsets.UTF_8);
        assertTrue(value.indexOf("first") < value.indexOf("second"));
        assertTrue(value.indexOf("second") < value.indexOf("third"));
        assertTrue(value.contains("[a]") && value.contains("[b]"));
    }

    @Test void rejectsTimestampRegression() throws Exception {
        StagedSource source = staged("a", "2026-07-13 10:00:02,000 [ACTIVE] INFO demo.A - later\n"
                + "2026-07-13 10:00:01,000 [ACTIVE] INFO demo.A - earlier\n");
        assertThrows(java.io.IOException.class, () -> new ChronologicalMerger().merge(Arrays.asList(source),
                temp.resolve("bad"), "bad.log", null, 1024, false));
    }

    @Test void rollsPartsWithoutExceedingLimit() throws Exception {
        StagedSource source = staged("a", "2026-07-13 10:00:00,000 [ACTIVE] INFO demo.A - one\n"
                + "2026-07-13 10:00:01,000 [ACTIVE] INFO demo.A - two\n");
        List<Path> output = new ChronologicalMerger().merge(Arrays.asList(source), temp.resolve("parts"),
                "combined.log", null, 80, false);
        assertEquals(2, output.size());
        for (Path path : output) assertTrue(Files.size(path) <= 80);
    }

    private StagedSource staged(String label, String text) throws Exception {
        ExpandedSource source = TestFixtures.source(label);
        Path path = temp.resolve(label + ".log");
        Files.write(path, text.getBytes(StandardCharsets.UTF_8));
        return new StagedSource(source, path, new RemoteMetadata(200, Files.size(path), "etag-" + label, 1, true));
    }
}
