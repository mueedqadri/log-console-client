package com.example.logconsole.ui;

import org.jline.terminal.Terminal;
import org.jline.utils.InfoCmp;

import java.io.IOException;
import java.util.List;

final class TerminalMenu {
    private final Terminal terminal;

    TerminalMenu(Terminal terminal) { this.terminal = terminal; }

    int select(String header, List<String> options) throws IOException {
        int selected = 0;
        terminal.enterRawMode();
        while (true) {
            clear();
            terminal.writer().println(header);
            terminal.writer().println();
            for (int i = 0; i < options.size(); i++) {
                terminal.writer().println((i == selected ? "> " : "  ") + (i + 1) + ". " + options.get(i));
            }
            terminal.writer().println("\nUse arrows or a number; Enter selects, Esc cancels.");
            terminal.flush();
            int value = terminal.reader().read();
            if (value == 3 || value == 27) {
                if (value == 27) {
                    int next = terminal.reader().read(35L);
                    if (next == '[') {
                        int direction = terminal.reader().read(35L);
                        if (direction == 'A') selected = (selected - 1 + options.size()) % options.size();
                        else if (direction == 'B') selected = (selected + 1) % options.size();
                        continue;
                    }
                }
                return -1;
            }
            if (value == '\r' || value == '\n') return selected;
            if (value >= '1' && value <= '9') {
                int index = value - '1';
                if (index < options.size()) return index;
            }
        }
    }

    void clear() {
        if (!terminal.puts(InfoCmp.Capability.clear_screen)) terminal.writer().print("\033[2J\033[H");
    }
}
