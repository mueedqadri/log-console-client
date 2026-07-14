package com.example.logconsole;

import com.example.logconsole.config.AppConfig;
import com.example.logconsole.model.ExpandedSource;

import java.net.URL;
import java.util.Arrays;
import java.util.LinkedHashMap;

public final class TestFixtures {
    private TestFixtures() { }

    public static AppConfig config(String baseUrl) {
        AppConfig config = new AppConfig();
        AppConfig.ConnectionConfig connection = new AppConfig.ConnectionConfig();
        connection.baseUrl = baseUrl;
        connection.defaultUsername = "user";
        config.connections.put("main", connection);

        AppConfig.EnvironmentConfig environment = new AppConfig.EnvironmentConfig();
        environment.displayName = "Test";
        environment.connection = "main";
        environment.timezone = "UTC";
        config.environments.put("test", environment);

        for (String id : Arrays.asList("a", "b")) {
            AppConfig.LocationConfig location = new AppConfig.LocationConfig();
            location.displayName = id.toUpperCase();
            location.properties.put("folder", "location-" + id);
            config.locations.put(id, location);
        }

        AppConfig.ParserConfig parser = new AppConfig.ParserConfig();
        parser.headerRegex = "^(?<timestamp>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2},\\d{3}) \\[[^]]+] (?<level>INFO|DEBUG|WARN|ERROR) (?<logger>[^ ]+) - (?<message>.*)$";
        parser.timestampPattern = "yyyy-MM-dd HH:mm:ss,SSS";
        config.parsers.put("standard", parser);

        AppConfig.ApplicationConfig app = new AppConfig.ApplicationConfig();
        app.displayName = "Example";
        app.application = "example.app";
        app.environment = "test";
        app.parser = "standard";
        app.locations.addAll(Arrays.asList("a", "b"));
        app.dimensions.put("cluster", Arrays.asList("01", "02"));
        app.dimensions.put("logNumber", Arrays.asList("01", "02"));
        app.currentPathTemplate = "logs/{location.folder}/{dimension.cluster}/{application}-{dimension.logNumber}.log";
        app.archivePathTemplate = "logs/{location.folder}/{dimension.cluster}/{application}-{dimension.logNumber}.{date}.log";
        app.sourceLabelTemplate = "{environment}/{location.id}/{dimension.cluster}/{dimension.logNumber}";
        app.outputFileTemplate = "{application}.combined.{date}.log";
        config.applications.put("example", app);
        return config;
    }

    public static ExpandedSource source(String label) throws Exception {
        AppConfig config = config("http://127.0.0.1:8080/");
        AppConfig.ConnectionConfig connection = config.connections.get("main");
        return new ExpandedSource("example", "Example", "test", "a",
                new LinkedHashMap<String, String>(), label, new URL("http://127.0.0.1/test.log"),
                connection, config.parsers.get("standard"), "UTC");
    }
}
