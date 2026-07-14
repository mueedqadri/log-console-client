package com.example.logconsole.ui;

import com.example.logconsole.model.ExpandedSource;
import com.example.logconsole.model.ParsedRecord;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.InfoCmp;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class StreamFrameRenderer {
    private static final int HEADER_ROWS = 4;
    private static final int FOOTER_ROWS = 1;

    private final Terminal terminal;
    private final LogHighlighter highlighter;
    private final boolean positioned;
    private final boolean alternate;

    StreamFrameRenderer(Terminal terminal, boolean color) {
        this.terminal = terminal;
        this.highlighter = new LogHighlighter(color);
        this.positioned = terminal.getStringCapability(InfoCmp.Capability.cursor_address) != null
                && terminal.getStringCapability(InfoCmp.Capability.clr_eol) != null;
        this.alternate = terminal.getStringCapability(InfoCmp.Capability.enter_ca_mode) != null
                && terminal.getStringCapability(InfoCmp.Capability.exit_ca_mode) != null;
    }

    void enter() {
        if (alternate) terminal.puts(InfoCmp.Capability.enter_ca_mode);
        terminal.puts(InfoCmp.Capability.cursor_invisible);
        terminal.puts(InfoCmp.Capability.clear_screen);
        terminal.flush();
    }

    void exit() {
        terminal.puts(InfoCmp.Capability.cursor_normal);
        if (alternate) terminal.puts(InfoCmp.Capability.exit_ca_mode);
        else terminal.writer().println();
        terminal.flush();
    }

    int pageSize() { return Math.max(1, terminal.getHeight() - HEADER_ROWS - FOOTER_ROWS); }

    void preparePrompt() {
        terminal.puts(InfoCmp.Capability.cursor_normal);
        if (positioned) {
            terminal.puts(InfoCmp.Capability.cursor_address, Math.max(0, terminal.getHeight() - 1), 0);
            terminal.puts(InfoCmp.Capability.clr_eol);
        } else terminal.writer().println();
        terminal.flush();
    }

    void finishPrompt() {
        terminal.puts(InfoCmp.Capability.cursor_invisible);
        terminal.flush();
    }

    void render(ExpandedSource source, List<ParsedRecord> records, int first, boolean compact,
                Set<String> levels, String search, int pending, String banner) {
        int page = pageSize();
        List<String> lines = new ArrayList<String>();
        lines.add("STREAM " + source.getLabel() + "  " + source.getUrl().getPath());
        lines.add("Levels=" + levels + " Filter=" + (search.isEmpty() ? "<none>" : search)
                + " Mode=" + (compact ? "compact" : "raw") + " Pending=" + pending);
        lines.add(banner);
        lines.add(repeat('-', Math.min(80, Math.max(1, terminal.getWidth() - 1))));
        int printed = 0;
        for (int i = first; i < records.size() && printed < page; i++) {
            String value = highlighter.render(records.get(i), compact);
            String[] recordLines = value.split("\\R", -1);
            for (String line : recordLines) {
                if (printed++ >= page) break;
                lines.add(line);
            }
        }
        while (printed++ < page) lines.add("");
        lines.add(footer(Math.max(1, terminal.getWidth() - 1)));
        paint(lines);
    }

    private void paint(List<String> lines) {
        int width = Math.max(1, terminal.getWidth() - 1);
        if (positioned) {
            int height = Math.min(lines.size(), terminal.getHeight());
            for (int row = 0; row < height; row++) {
                terminal.puts(InfoCmp.Capability.cursor_address, row, 0);
                terminal.puts(InfoCmp.Capability.clr_eol);
                write(lines.get(row), width);
            }
        } else {
            terminal.puts(InfoCmp.Capability.clear_screen);
            for (String line : lines) terminal.writer().println(plain(line, width));
        }
        terminal.flush();
    }

    private void write(String value, int width) {
        AttributedString styled = AttributedString.fromAnsi(value);
        if (styled.columnLength() > width) styled = styled.columnSubSequence(0, width);
        terminal.writer().print(styled.toAnsi(terminal));
    }

    private static String plain(String value, int width) {
        AttributedString styled = AttributedString.fromAnsi(value);
        if (styled.columnLength() > width) styled = styled.columnSubSequence(0, width);
        return styled.toString();
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }

    private static String footer(int width) {
        if (width >= 110) {
            return "r refresh | Up/Down PgUp/PgDn Home/End | 1 INFO 2 DEBUG 3 WARN 4 ERROR | / filter | c raw | f follow | q back";
        }
        if (width >= 70) {
            return "r refresh | arrows/PgUp/PgDn | 1-4 levels | / filter | c raw | f follow | q back";
        }
        return "r refresh | arrows scroll | / filter | c raw | f follow | q back";
    }
}
