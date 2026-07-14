package com.example.logconsole.ui;

import com.example.logconsole.model.ExpandedSource;
import org.jline.terminal.Terminal;
import org.jline.utils.InfoCmp;

import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class DownloadProgressDisplay {
    private static final long INTERACTIVE_REFRESH_NANOS = 100_000_000L;
    private static final long FALLBACK_REFRESH_NANOS = 1_000_000_000L;

    private final Terminal terminal;
    private final List<String> labels = new ArrayList<String>();
    private final long[] lastPaint;
    private final boolean interactive;
    private final char filled;
    private final char empty;

    DownloadProgressDisplay(Terminal terminal, List<ExpandedSource> sources) {
        this.terminal = terminal;
        for (ExpandedSource source : sources) labels.add(source.getLabel());
        this.lastPaint = new long[sources.size()];
        this.interactive = supportsRows(terminal, sources.size());
        CharsetEncoder encoder = terminal.encoding().newEncoder();
        boolean unicode = encoder.canEncode("█░");
        this.filled = unicode ? '█' : '#';
        this.empty = unicode ? '░' : '-';
    }

    synchronized void open(String date) {
        terminal.writer().println();
        terminal.writer().println("Downloading " + labels.size() + " sources for " + date + "...");
        if (interactive) {
            for (int i = 0; i < labels.size(); i++) terminal.writer().println(line(i, 0, -1));
        }
        terminal.flush();
    }

    synchronized void update(int sourceNumber, long completed, long total) {
        int index = sourceNumber - 1;
        if (index < 0 || index >= labels.size()) return;
        long now = System.nanoTime();
        boolean complete = total >= 0 && completed >= total;
        long interval = interactive ? INTERACTIVE_REFRESH_NANOS : FALLBACK_REFRESH_NANOS;
        if (!complete && completed > 0 && now - lastPaint[index] < interval) return;
        lastPaint[index] = now;
        String value = line(index, completed, total);
        if (interactive) replaceRow(index, value);
        else terminal.writer().println(value);
        terminal.flush();
    }

    synchronized void close() {
        terminal.flush();
    }

    String line(int index, long completed, long total) {
        return formatLine(index + 1, labels.size(), labels.get(index), completed, total,
                Math.max(20, terminal.getWidth()), filled, empty);
    }

    static String formatLine(int number, int count, String label, long completed, long total,
                             int width, char filled, char empty) {
        String source = String.format(Locale.ROOT, "%2d/%-2d ", number, count);
        if (total <= 0) {
            return fit(source + label + " | " + formatBytes(completed) + " downloaded", width);
        }
        double ratio = Math.max(0.0, Math.min(1.0, completed / (double) total));
        String percent = String.format(Locale.ROOT, "%6.1f%%", ratio * 100.0);
        if (width < 54) {
            int barLength = Math.max(5, Math.min(10, width - 30));
            String bar = bar(barLength, ratio, filled, empty);
            int labelWidth = Math.max(1, width - source.length() - bar.length() - percent.length() - 4);
            return fit(source + "[" + bar + "] " + percent + " " + trim(label, labelWidth), width);
        }
        int barLength = Math.max(10, Math.min(30, width - 60));
        String downloaded = String.format(Locale.ROOT, "%10s", formatBytes(completed));
        String expected = String.format(Locale.ROOT, "%-10s", formatBytes(total));
        int fixed = source.length() + barLength + percent.length() + downloaded.length() + expected.length() + 14;
        int labelWidth = Math.max(1, width - fixed);
        String value = source + trim(label, labelWidth) + " | [" + bar(barLength, ratio, filled, empty)
                + "] | " + percent + " | " + downloaded + " / " + expected;
        return fit(value, width);
    }

    private void replaceRow(int index, String value) {
        int rowsUp = labels.size() - index;
        terminal.writer().print('\r');
        for (int i = 0; i < rowsUp; i++) terminal.puts(InfoCmp.Capability.cursor_up);
        terminal.puts(InfoCmp.Capability.clr_eol);
        terminal.writer().print(value);
        terminal.writer().print('\r');
        for (int i = 0; i < rowsUp; i++) terminal.puts(InfoCmp.Capability.cursor_down);
    }

    private static boolean supportsRows(Terminal terminal, int rows) {
        return !Terminal.TYPE_DUMB.equals(terminal.getType())
                && rows + 2 < terminal.getHeight()
                && terminal.getStringCapability(InfoCmp.Capability.cursor_up) != null
                && terminal.getStringCapability(InfoCmp.Capability.cursor_down) != null
                && terminal.getStringCapability(InfoCmp.Capability.clr_eol) != null;
    }

    private static String bar(int length, double ratio, char filled, char empty) {
        int filledLength = (int) Math.round(length * ratio);
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) result.append(i < filledLength ? filled : empty);
        return result.toString();
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double value = bytes;
        String[] units = {"KiB", "MiB", "GiB", "TiB"};
        int unit = -1;
        do { value /= 1024.0; unit++; } while (value >= 1024.0 && unit < units.length - 1);
        return String.format(Locale.ROOT, "%.2f %s", value, units[unit]);
    }

    private static String fit(String value, int width) {
        return value.length() <= width ? value : trim(value, width);
    }

    private static String trim(String value, int width) {
        if (width <= 0) return "";
        if (value.length() <= width) return value;
        if (width <= 3) return value.substring(0, width);
        return value.substring(0, width - 3) + "...";
    }
}
