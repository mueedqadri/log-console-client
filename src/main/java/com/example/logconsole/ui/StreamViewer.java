package com.example.logconsole.ui;

import com.example.logconsole.config.AppConfig;
import com.example.logconsole.http.RangeHttpClient;
import com.example.logconsole.model.ExpandedSource;
import com.example.logconsole.model.ParsedRecord;
import com.example.logconsole.stream.RecordFilter;
import com.example.logconsole.stream.TailSession;
import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class StreamViewer {
    private final Terminal terminal;
    private final LineReader lineReader;
    private final RangeHttpClient client;
    private final AppConfig.StreamConfig settings;
    private final TerminalMenu menu;

    StreamViewer(Terminal terminal, LineReader lineReader, RangeHttpClient client, AppConfig.StreamConfig settings) {
        this.terminal = terminal;
        this.lineReader = lineReader;
        this.client = client;
        this.settings = settings;
        this.menu = new TerminalMenu(terminal);
    }

    void open(ExpandedSource source) throws IOException {
        TailSession session = new TailSession(source, client, settings);
        session.open();
        Set<String> levels = new LinkedHashSet<String>(source.getParser().levels);
        String search = "";
        boolean compact = true;
        boolean follow = true;
        int firstRecord = 0;
        int previousCount = 0;
        int pending = 0;
        String banner = "Connected";
        terminal.enterRawMode();
        while (true) {
            RecordFilter filter = new RecordFilter(levels, search, false);
            List<ParsedRecord> records = session.records(filter);
            int page = Math.max(1, terminal.getHeight() - 7);
            if (follow) firstRecord = Math.max(0, records.size() - page);
            else firstRecord = Math.min(firstRecord, Math.max(0, records.size() - 1));
            render(source, records, firstRecord, page, compact, levels, search, pending, banner);
            previousCount = records.size();
            int key = terminal.reader().read(settings.pollMillis);
            if (key == NonBlockingReader.READ_EXPIRED) {
                TailSession.Change change = session.poll();
                List<ParsedRecord> updated = session.records(filter);
                if (!follow && updated.size() > previousCount) pending += updated.size() - previousCount;
                if (change == TailSession.Change.ROTATED) banner = "Log rotated/truncated; tail reset";
                else if (change == TailSession.Change.APPENDED) banner = "New records received";
                continue;
            }
            if (key == 3 || key == 'q') return;
            if (key == 27) {
                int next = terminal.reader().read(35L);
                if (next != '[') return;
                int code = terminal.reader().read(35L);
                if (code == 'A') {
                    follow = false;
                    if (firstRecord == 0 && session.loadOlder()) banner = "Loaded older range";
                    else firstRecord = Math.max(0, firstRecord - 1);
                } else if (code == 'B') {
                    firstRecord++;
                    if (firstRecord >= Math.max(0, records.size() - page)) { follow = true; pending = 0; }
                } else if (code == '5') {
                    terminal.reader().read(35L);
                    follow = false;
                    if (firstRecord == 0) session.loadOlder();
                    firstRecord = Math.max(0, firstRecord - page);
                } else if (code == '6') {
                    terminal.reader().read(35L);
                    firstRecord += page;
                } else if (code == 'H' || code == '1') {
                    if (code == '1') terminal.reader().read(35L);
                    follow = false;
                    while (session.loadOlder()) { }
                    firstRecord = 0;
                } else if (code == 'F' || code == '4') {
                    if (code == '4') terminal.reader().read(35L);
                    follow = true; pending = 0;
                }
                continue;
            }
            if (key == 'c') compact = !compact;
            else if (key == 'f') { follow = true; pending = 0; }
            else if (key == '/') {
                search = lineReader.readLine("\nFilter text (blank clears): ").trim();
                firstRecord = 0;
            } else if (key >= '1' && key <= '4') {
                String[] standard = {"INFO", "DEBUG", "WARN", "ERROR"};
                String level = standard[key - '1'];
                if (levels.contains(level)) levels.remove(level); else levels.add(level);
                firstRecord = 0;
            }
        }
    }

    private void render(ExpandedSource source, List<ParsedRecord> records, int first, int page, boolean compact,
                        Set<String> levels, String search, int pending, String banner) {
        menu.clear();
        terminal.writer().println("STREAM " + source.getLabel() + "  " + source.getUrl().getPath());
        terminal.writer().println("Levels=" + levels + " Filter=" + (search.isEmpty() ? "<none>" : search)
                + " Mode=" + (compact ? "compact" : "raw") + " Pending=" + pending);
        terminal.writer().println(banner);
        terminal.writer().println("--------------------------------------------------------------------------------");
        int printed = 0;
        for (int i = first; i < records.size() && printed < page; i++) {
            String value = compact ? records.get(i).renderCompact() : records.get(i).renderRaw();
            String[] lines = value.split("\\R", -1);
            for (String line : lines) {
                if (printed++ >= page) break;
                terminal.writer().println(line);
            }
        }
        while (printed++ < page) terminal.writer().println();
        terminal.writer().println("↑↓ PgUp/PgDn Home/End | 1 INFO 2 DEBUG 3 WARN 4 ERROR | / filter | c raw | f follow | Esc/q back");
        terminal.flush();
    }
}
