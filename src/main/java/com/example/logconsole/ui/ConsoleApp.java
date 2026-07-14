package com.example.logconsole.ui;

import com.example.logconsole.config.AppConfig;
import com.example.logconsole.config.SourceExpander;
import com.example.logconsole.config.ConfigResolver;
import com.example.logconsole.download.DownloadService;
import com.example.logconsole.http.CredentialManager;
import com.example.logconsole.http.RangeHttpClient;
import com.example.logconsole.http.StatusService;
import com.example.logconsole.model.ApplicationStatus;
import com.example.logconsole.model.ExpandedSource;
import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class ConsoleApp {
    private final AppConfig config;
    private final Terminal terminal;
    private final LineReader lineReader;
    private final CredentialManager credentials;
    private final RangeHttpClient client;
    private final TerminalMenu menu;
    private final ConsoleRenderer renderer;
    private final SourceExpander expander;
    private final boolean color;

    public ConsoleApp(AppConfig config, Terminal terminal, LineReader lineReader, CredentialManager credentials,
                      boolean color, boolean debug) {
        this.config = config;
        this.terminal = terminal;
        this.lineReader = lineReader;
        this.credentials = credentials;
        this.client = new RangeHttpClient(credentials, debug);
        this.menu = new TerminalMenu(terminal);
        this.renderer = new ConsoleRenderer(color);
        this.expander = new SourceExpander(config);
        this.color = color;
    }

    public void run() throws IOException {
        List<ApplicationStatus> statuses = new StatusService(config, client).refresh();
        while (true) {
            int action = menu.select(renderer.dashboard(statuses), Arrays.asList(
                    "Download today's logs", "Stream today's log", "Refresh status", "Change username", "Quit"));
            if (action < 0 || action == 4) return;
            try {
                if (action == 0) download();
                else if (action == 1) stream();
                else if (action == 2) statuses = new StatusService(config, client).refresh();
                else if (action == 3) { credentials.changeUsernames(); statuses = new StatusService(config, client).refresh(); }
            } catch (Exception e) {
                lineReader.readLine("\nOperation failed: " + e.getMessage() + "\nPress Enter to continue...");
            }
        }
    }

    private void stream() throws IOException {
        String applicationId = chooseApplication("Choose application to stream");
        if (applicationId == null) return;
        List<ExpandedSource> sources = expander.expandCurrent(applicationId);
        int selected = menu.select("Choose exact source", labels(sources));
        if (selected < 0) return;
        new StreamViewer(terminal, lineReader, client,
                ConfigResolver.streaming(config, config.applications.get(applicationId)), color).open(sources.get(selected));
    }

    private void download() throws IOException {
        String applicationId = chooseApplication("Choose application to download");
        if (applicationId == null) return;
        AppConfig.ApplicationConfig application = config.applications.get(applicationId);
        AppConfig.EnvironmentConfig environment = config.environments.get(application.environment);
        LocalDate today = LocalDate.now(ZoneId.of(environment.timezone));
        List<ExpandedSource> sources = expander.expandCurrent(applicationId);
        DownloadService service = new DownloadService(config, client);
        final DownloadProgressDisplay display = new DownloadProgressDisplay(terminal, sources);
        display.open(today.toString());
        Path output;
        try {
            output = service.download(applicationId, today, sources,
                    new DownloadService.Progress() {
                        @Override public void update(int sourceNumber, int sourceCount, ExpandedSource source,
                                                     long completed, long total) {
                            display.update(sourceNumber, completed, total);
                        }
                    });
        } finally { display.close(); }
        terminal.writer().println("Published:\n  " + output.toAbsolutePath());
        terminal.flush();
        lineReader.readLine("Press Enter to continue...");
    }

    private String chooseApplication(String title) throws IOException {
        List<String> ids = new ArrayList<String>();
        List<String> names = new ArrayList<String>();
        for (Map.Entry<String, AppConfig.ApplicationConfig> entry : config.applications.entrySet()) {
            ids.add(entry.getKey());
            names.add(entry.getValue().displayName == null ? entry.getKey() : entry.getValue().displayName);
        }
        int selected = menu.select(title, names);
        return selected < 0 ? null : ids.get(selected);
    }

    private static List<String> labels(List<ExpandedSource> sources) {
        List<String> labels = new ArrayList<String>();
        for (ExpandedSource source : sources) labels.add(source.getLabel() + "  " + source.getUrl().getPath());
        return labels;
    }

}
