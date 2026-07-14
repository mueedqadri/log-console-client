package com.example.logconsole.ui;

import com.example.logconsole.config.AppConfig;
import com.example.logconsole.http.RangeHttpClient;
import com.example.logconsole.model.ExpandedSource;
import com.example.logconsole.model.ParsedRecord;
import com.example.logconsole.stream.RecordFilter;
import com.example.logconsole.stream.TailSession;
import org.jline.terminal.Attributes;
import org.jline.terminal.MouseEvent;
import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class StreamViewer {
    private final Terminal terminal;
    private final LineReader lineReader;
    private final RangeHttpClient client;
    private final AppConfig.StreamConfig settings;
    private final StreamFrameRenderer screen;

    StreamViewer(Terminal terminal, LineReader lineReader, RangeHttpClient client, AppConfig.StreamConfig settings,
                 boolean color) {
        this.terminal = terminal;
        this.lineReader = lineReader;
        this.client = client;
        this.settings = settings;
        this.screen = new StreamFrameRenderer(terminal, color);
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
        String banner = "Connected - press r to refresh";
        Attributes savedAttributes = terminal.enterRawMode();
        boolean mouseTracking = terminal.hasMouseSupport()
                && terminal.trackMouse(Terminal.MouseTracking.Normal);
        try {
            screen.enter();
            while (true) {
                RecordFilter filter = new RecordFilter(levels, search, false);
                List<ParsedRecord> records = session.records(filter);
                int page = screen.pageSize();
                if (follow) firstRecord = Math.max(0, records.size() - page);
                else firstRecord = Math.min(firstRecord, Math.max(0, records.size() - 1));
                screen.render(source, records, firstRecord, compact, levels, search, pending, banner);
                previousCount = records.size();
                int key = terminal.reader().read();
                if (key == 3 || key == 'q') return;
                if (key == 'r' || key == 'R') {
                    TailSession.Change change = session.poll();
                    List<ParsedRecord> updated = session.records(filter);
                    if (!follow && updated.size() > previousCount) pending += updated.size() - previousCount;
                    if (change == TailSession.Change.ROTATED) banner = "Log rotated/truncated; tail reset";
                    else if (change == TailSession.Change.APPENDED) banner = "New records received";
                    else banner = "Already up to date";
                    continue;
                }
                // Some Windows console hosts emit extended scan codes instead of ANSI CSI arrows.
                if (key == 0 || key == 224) {
                    int code = terminal.reader().read(35L);
                    if (code == 72) {
                        follow = false;
                        if (firstRecord == 0 && session.loadOlder()) banner = "Loaded older range";
                        else firstRecord = Math.max(0, firstRecord - 1);
                    } else if (code == 80) {
                        firstRecord++;
                        if (firstRecord >= Math.max(0, records.size() - page)) { follow = true; pending = 0; }
                    }
                    continue;
                }
                if (key == 27) {
                    int next = terminal.reader().read(35L);
                    // Mouse reports and terminal-specific key chords are CSI escape sequences too.
                    // They are not navigation requests, so never let an incomplete/unknown sequence exit streaming.
                    if (next != '[') continue;
                    int code = terminal.reader().read(35L);
                    if (code == '<' || code == 'M') {
                        MouseEvent event = terminal.readMouseEvent(code == '<' ? "\033[<" : "\033[M");
                        if (event.getType() == MouseEvent.Type.Wheel && event.getButton() == MouseEvent.Button.WheelUp) {
                            follow = false;
                            if (firstRecord == 0 && session.loadOlder()) banner = "Loaded older range";
                            else firstRecord = Math.max(0, firstRecord - 1);
                        } else if (event.getType() == MouseEvent.Type.Wheel
                                && event.getButton() == MouseEvent.Button.WheelDown) {
                            firstRecord++;
                            if (firstRecord >= Math.max(0, records.size() - page)) { follow = true; pending = 0; }
                        }
                        continue;
                    }
                    if (code == 'A') {
                        follow = false;
                        if (firstRecord == 0 && session.loadOlder()) banner = "Loaded older range";
                        else firstRecord = Math.max(0, firstRecord - 1);
                    } else if (code == 'B') {
                        firstRecord++;
                        if (firstRecord >= Math.max(0, records.size() - page)) { follow = true; pending = 0; }
                    }
                    continue;
                }
                if (key == 'c') compact = !compact;
                else if (key == '/') {
                    screen.preparePrompt();
                    try { search = lineReader.readLine("Filter text (blank clears): ").trim(); }
                    finally { screen.finishPrompt(); }
                    firstRecord = 0;
                } else if (key >= '1' && key <= '4') {
                    String[] standard = {"INFO", "DEBUG", "WARN", "ERROR"};
                    String level = standard[key - '1'];
                    if (levels.contains(level)) levels.remove(level); else levels.add(level);
                    firstRecord = 0;
                }
            }
        } finally {
            if (mouseTracking) terminal.trackMouse(Terminal.MouseTracking.Off);
            screen.exit();
            terminal.setAttributes(savedAttributes);
        }
    }
}
