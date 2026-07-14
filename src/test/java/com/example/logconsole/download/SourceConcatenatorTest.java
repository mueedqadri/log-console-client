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

import static org.junit.jupiter.api.Assertions.assertEquals;

class SourceConcatenatorTest {
    @TempDir Path temp;

    @Test void preservesSourceOrderAndSeparatesRawLogsWithHeaders() throws Exception {
        StagedSource first = staged("location-a", "first source without a trailing newline");
        StagedSource second = staged("location-b", "second source\n");

        Path output = new SourceConcatenator().concatenate(Arrays.asList(first, second), temp.resolve("out"),
                "combined.log", false);

        String newline = System.lineSeparator();
        assertEquals("===== SOURCE: location-a =====" + newline
                        + "first source without a trailing newline" + newline
                        + "===== SOURCE: location-b =====" + newline
                        + "second source" + newline,
                new String(Files.readAllBytes(output), StandardCharsets.UTF_8));
    }

    private StagedSource staged(String label, String text) throws Exception {
        ExpandedSource source = TestFixtures.source(label);
        Path path = temp.resolve(label + ".log");
        Files.write(path, text.getBytes(StandardCharsets.UTF_8));
        return new StagedSource(source, path, new RemoteMetadata(200, Files.size(path), "etag-" + label, 1, true));
    }
}
