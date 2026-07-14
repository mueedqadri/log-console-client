package com.example.logconsole;

import com.example.logconsole.config.AppConfig;
import com.example.logconsole.config.ConfigLoader;
import com.example.logconsole.config.SettingsStore;
import com.example.logconsole.http.CredentialManager;
import com.example.logconsole.ui.ConsoleApp;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

public final class App {
    private App() { }

    public static void main(String[] args) {
        int code = run(args);
        if (code != 0) System.exit(code);
    }

    static int run(String[] args) {
        try {
            CliOptions options = CliOptions.parse(args);
            if (options.help) { System.out.println(CliOptions.usage()); return 0; }
            AppConfig config = new ConfigLoader().load(options.config);
            try (Terminal terminal = TerminalBuilder.builder().system(true).dumb(true).build()) {
                LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();
                try (CredentialManager credentials = new CredentialManager(config, reader, new SettingsStore(), options.username)) {
                    boolean color = !options.noColor && !Terminal.TYPE_DUMB.equals(terminal.getType());
                    new ConsoleApp(config, terminal, reader, credentials, color, options.debug).run();
                }
            }
            return 0;
        } catch (IllegalArgumentException e) {
            System.err.println("Configuration/argument error: " + e.getMessage());
            System.err.println(CliOptions.usage());
            return 2;
        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            return 1;
        }
    }
}
