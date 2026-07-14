package com.example.logconsole.ui;

import com.example.logconsole.model.ApplicationStatus;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

final class ConsoleRenderer {
    private final boolean color;

    ConsoleRenderer(boolean color) { this.color = color; }

    String dashboard(List<ApplicationStatus> statuses) {
        StringBuilder result = new StringBuilder("LOG CONSOLE  ");
        result.append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append('\n');
        result.append(String.format("%-24s %-10s %-9s %-12s %s%n", "Application", "Env", "Sources", "Size", "Status"));
        result.append("---------------------------------------------------------------------\n");
        for (ApplicationStatus status : statuses) {
            String state = color(status.getState(), status.getState().name());
            result.append(String.format("%-24s %-10s %d/%-7d %-12s %s",
                    trim(status.getDisplayName(), 24), status.getEnvironment(), status.getAvailable(),
                    status.getSources(), size(status.getTotalBytes()), state));
            if (status.getState() == ApplicationStatus.State.GRAY) result.append("  ").append(status.getDetail());
            result.append('\n');
        }
        return result.toString();
    }

    private String color(ApplicationStatus.State state, String value) {
        if (!color) return value;
        String code = state == ApplicationStatus.State.GREEN ? "32" : state == ApplicationStatus.State.YELLOW ? "33"
                : state == ApplicationStatus.State.RED ? "31" : "90";
        return "\033[" + code + "m" + value + "\033[0m";
    }

    private static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double value = bytes;
        String[] units = {"KiB", "MiB", "GiB", "TiB"};
        int unit = -1;
        do { value /= 1024; unit++; } while (value >= 1024 && unit < units.length - 1);
        return new DecimalFormat("0.0").format(value) + " " + units[unit];
    }

    private static String trim(String value, int max) { return value.length() <= max ? value : value.substring(0, max - 1) + "…"; }
}
