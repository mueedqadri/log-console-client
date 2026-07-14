package com.example.logconsole.ui;

import com.example.logconsole.model.ApplicationStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleRendererTest {
    @Test void colorsSizeWithoutRenderingAStatusColumn() {
        ApplicationStatus status = new ApplicationStatus("app", "Application", "test", 2, 2,
                12 * 1024 * 1024L, ApplicationStatus.State.YELLOW, "OK");

        String rendered = new ConsoleRenderer(true).dashboard(Arrays.asList(status));

        assertFalse(rendered.contains("Status"));
        assertFalse(rendered.contains("YELLOW"));
        assertTrue(rendered.contains("\033[33m12.0 MiB\033[0m"));
    }

    @Test void retainsGrayFailureDetailWithoutColor() {
        ApplicationStatus status = new ApplicationStatus("app", "Application", "test", 2, 1,
                1024, ApplicationStatus.State.GRAY, "HTTP 404");

        String rendered = new ConsoleRenderer(false).dashboard(Arrays.asList(status));

        assertTrue(rendered.contains("1.0 KiB  HTTP 404"));
        assertFalse(rendered.contains("GRAY"));
    }
}
