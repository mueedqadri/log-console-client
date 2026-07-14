package com.example.logconsole.ui;

import com.example.logconsole.config.AppConfig;
import com.example.logconsole.config.SourceExpander;
import com.example.logconsole.config.ConfigResolver;
import com.example.logconsole.download.DownloadService;
import com.example.logconsole.download.DownloadStager;
import com.example.logconsole.http.CredentialManager;
import com.example.logconsole.http.RangeHttpClient;
import com.example.logconsole.http.StatusService;
import com.example.logconsole.model.ApplicationStatus;
import com.example.logconsole.model.ExpandedSource;
import com.example.logconsole.stream.RecordFilter;
import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ConsoleApp {
    private final AppConfig config;
    private final Terminal terminal;
    private final LineReader lineReader;
    private final CredentialManager credentials;
    private final RangeHttpClient client;
    private final TerminalMenu menu;
    private final ConsoleRenderer renderer;
    private final SourceExpander expander;

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
    }

    public void run() throws IOException {
        List<ApplicationStatus> statuses = new StatusService(config, client).refresh();
        while (true) {
            int action = menu.select(renderer.dashboard(statuses), Arrays.asList(
                    "Download logs", "Stream today's log", "Refresh status", "Change username", "Quit"));
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
                ConfigResolver.streaming(config, config.applications.get(applicationId))).open(sources.get(selected));
    }

    private void download() throws IOException {
        String applicationId = chooseApplication("Choose application to download");
        if (applicationId == null) return;
        AppConfig.ApplicationConfig application = config.applications.get(applicationId);
        AppConfig.EnvironmentConfig environment = config.environments.get(application.environment);
        LocalDate today = LocalDate.now(ZoneId.of(environment.timezone));
        String dateValue = lineReader.readLine("Date [today or yyyy-MM-dd] (today): ").trim();
        boolean current = dateValue.isEmpty() || "today".equalsIgnoreCase(dateValue);
        LocalDate date = current ? today : LocalDate.parse(dateValue);
        List<ExpandedSource> sources = current ? expander.expandCurrent(applicationId) : expander.expandArchive(applicationId, date);
        terminal.writer().println("\nSources (all selected by default):");
        for (int i = 0; i < sources.size(); i++) terminal.writer().println("  [x] " + (i + 1) + ". " + sources.get(i).getLabel());
        terminal.flush();
        String deselected = lineReader.readLine("Numbers to deselect, comma-separated (Enter keeps all): ").trim();
        Set<Integer> excluded = parseNumbers(deselected, sources.size());
        List<ExpandedSource> selected = new ArrayList<ExpandedSource>();
        for (int i = 0; i < sources.size(); i++) if (!excluded.contains(i + 1)) selected.add(sources.get(i));
        String levelText = lineReader.readLine("Export levels comma-separated (blank=all): ").trim();
        String search = lineReader.readLine("Export text filter (blank=none): ").trim();
        RecordFilter filter = null;
        if (!levelText.isEmpty() || !search.isEmpty()) {
            Set<String> levels = new LinkedHashSet<String>();
            if (!levelText.isEmpty()) for (String level : levelText.split(",")) levels.add(level.trim().toUpperCase());
            filter = new RecordFilter(levels, search);
        }
        boolean overwrite = "y".equalsIgnoreCase(lineReader.readLine("Overwrite existing outputs if needed? [y/N]: ").trim());
        DownloadService service = new DownloadService(config, client);
        List<Path> output = service.download(applicationId, date, selected, filter, overwrite,
                new DownloadStager.Progress() {
                    @Override public void update(ExpandedSource source, long completed, long total) {
                        terminal.writer().print("\rStaging " + source.getLabel() + " " + completed + "/" + total + " bytes");
                        terminal.flush();
                    }
                });
        terminal.writer().println("\nPublished:");
        for (Path path : output) terminal.writer().println("  " + path.toAbsolutePath());
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

    private static Set<Integer> parseNumbers(String value, int max) {
        Set<Integer> numbers = new LinkedHashSet<Integer>();
        if (value.isEmpty()) return numbers;
        for (String part : value.split(",")) {
            int number = Integer.parseInt(part.trim());
            if (number < 1 || number > max) throw new IllegalArgumentException("Source number out of range: " + number);
            numbers.add(number);
        }
        return numbers;
    }
}
