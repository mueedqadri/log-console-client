package com.example.logconsole.http;

import com.example.logconsole.config.AppConfig;
import com.example.logconsole.config.SourceExpander;
import com.example.logconsole.config.ConfigResolver;
import com.example.logconsole.model.ApplicationStatus;
import com.example.logconsole.model.ExpandedSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class StatusService {
    private final AppConfig config;
    private final SourceExpander expander;
    private final RangeHttpClient client;

    public StatusService(AppConfig config, RangeHttpClient client) {
        this.config = config;
        this.expander = new SourceExpander(config);
        this.client = client;
    }

    public List<ApplicationStatus> refresh() {
        List<ApplicationStatus> statuses = new ArrayList<ApplicationStatus>();
        for (Map.Entry<String, AppConfig.ApplicationConfig> entry : config.applications.entrySet()) {
            statuses.add(refreshOne(entry.getKey(), entry.getValue()));
        }
        return statuses;
    }

    private ApplicationStatus refreshOne(String id, AppConfig.ApplicationConfig application) {
        List<ExpandedSource> sources = expander.expandCurrent(id);
        int concurrency = sources.isEmpty() ? 1 : Math.min(sources.size(), sources.get(0).getConnection().concurrency);
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, concurrency));
        List<Future<RemoteMetadata>> futures = new ArrayList<Future<RemoteMetadata>>();
        for (final ExpandedSource source : sources) {
            futures.add(executor.submit(new Callable<RemoteMetadata>() {
                @Override public RemoteMetadata call() throws Exception {
                    return client.metadata(source.getUrl(), source.getConnection());
                }
            }));
        }
        executor.shutdown();
        long total = 0;
        int available = 0;
        String detail = "OK";
        for (Future<RemoteMetadata> future : futures) {
            try {
                RemoteMetadata metadata = future.get();
                if (metadata.isAvailable()) {
                    available++;
                    if (metadata.getLength() > 0) total += metadata.getLength();
                } else detail = "HTTP " + metadata.getStatusCode();
            } catch (Exception e) { detail = e.getCause() == null ? e.getMessage() : e.getCause().getMessage(); }
        }
        AppConfig.ThresholdsConfig thresholds = ConfigResolver.thresholds(config, application);
        ApplicationStatus.State state;
        if (available != sources.size()) state = ApplicationStatus.State.GRAY;
        else if (total >= thresholds.criticalBytes) state = ApplicationStatus.State.RED;
        else if (total >= thresholds.warningBytes) state = ApplicationStatus.State.YELLOW;
        else state = ApplicationStatus.State.GREEN;
        return new ApplicationStatus(id, application.displayName == null ? id : application.displayName,
                application.environment, sources.size(), available, total, state, detail);
    }
}
