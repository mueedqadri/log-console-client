package com.example.logconsole.ui;

import com.example.logconsole.TestFixtures;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamFrameRendererTest {
    @Test void rendersHeaderViewportAndRefreshFooterOnDumbTerminal() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (Terminal terminal = TerminalBuilder.builder()
                .streams(new ByteArrayInputStream(new byte[0]), output)
                .encoding(StandardCharsets.UTF_8).dumb(true).build()) {
            terminal.setSize(new Size(80, 10));
            StreamFrameRenderer renderer = new StreamFrameRenderer(terminal, false);

            renderer.enter();
            assertEquals(5, renderer.pageSize());
            renderer.render(TestFixtures.source("test/a"), Collections.emptyList(), 0, true,
                    new LinkedHashSet<String>(Collections.singletonList("INFO")), "", 0, "Connected");
            renderer.exit();
        }

        String rendered = new String(output.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(rendered.contains("STREAM test/a"));
        assertTrue(rendered.contains("Connected"));
        assertTrue(rendered.contains("r refresh"));
    }
}
