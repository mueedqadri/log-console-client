package com.example.logconsole.config;

import com.example.logconsole.model.ExpandedSource;

import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SourceExpander {
    private final AppConfig config;

    public SourceExpander(AppConfig config) {
        this.config = config;
    }

    public List<ExpandedSource> expandCurrent(String applicationId) {
        return expand(applicationId, null);
    }

    public List<ExpandedSource> expandArchive(String applicationId, LocalDate date) {
        if (date == null) throw new IllegalArgumentException("Archive date is required");
        return expand(applicationId, date);
    }

    private List<ExpandedSource> expand(String applicationId, LocalDate date) {
        AppConfig.ApplicationConfig application = require(config.applications, applicationId, "application");
        AppConfig.EnvironmentConfig environment = require(config.environments, application.environment, "environment");
        AppConfig.ConnectionConfig connection = require(config.connections, environment.connection, "connection");
        AppConfig.ParserConfig parser = require(config.parsers, application.parser, "parser");
        List<Map<String, String>> combinations = dimensionCombinations(application.dimensions);
        List<ExpandedSource> sources = new ArrayList<ExpandedSource>();
        Set<String> urls = new HashSet<String>();

        for (String locationId : application.locations) {
            AppConfig.LocationConfig location = require(config.locations, locationId, "location");
            for (Map<String, String> dimensions : combinations) {
                Map<String, String> values = variables(applicationId, application, environment, locationId,
                        location, dimensions, date);
                String pathTemplate = date == null ? application.currentPathTemplate : application.archivePathTemplate;
                String path = TemplateEngine.render(pathTemplate, values);
                URL url = resolve(connection.baseUrl, path);
                validateTransport(url, connection.allowInsecureHttp);
                if (!urls.add(url.toExternalForm())) {
                    throw new IllegalArgumentException("Duplicate expanded source URL: " + url);
                }
                String label = TemplateEngine.render(application.sourceLabelTemplate, values);
                sources.add(new ExpandedSource(applicationId,
                        application.displayName == null ? applicationId : application.displayName,
                        application.environment, locationId, dimensions, label, url, connection, parser,
                        parser.timezone == null ? environment.timezone : parser.timezone));
            }
        }
        return sources;
    }

    private static Map<String, String> variables(String applicationId, AppConfig.ApplicationConfig application,
                                                  AppConfig.EnvironmentConfig environment, String locationId,
                                                  AppConfig.LocationConfig location, Map<String, String> dimensions,
                                                  LocalDate date) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("application", application.application == null ? applicationId : application.application);
        values.put("application.id", applicationId);
        values.put("environment", application.environment);
        values.put("environment.name", environment.displayName == null ? application.environment : environment.displayName);
        values.put("location.id", locationId);
        values.put("location.name", location.displayName == null ? locationId : location.displayName);
        for (Map.Entry<String, String> property : location.properties.entrySet()) {
            values.put("location." + property.getKey(), property.getValue());
        }
        for (Map.Entry<String, String> dimension : dimensions.entrySet()) {
            values.put("dimension." + dimension.getKey(), dimension.getValue());
        }
        if (date != null) values.put("date", date.format(java.time.format.DateTimeFormatter.ofPattern(application.archiveDatePattern)));
        return values;
    }

    private static URL resolve(String baseUrl, String path) {
        try {
            URI base = new URI(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
            URI resolved = base.resolve(path.startsWith("/") ? path.substring(1) : path);
            return resolved.toURL();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid source URL from base=" + baseUrl + " path=" + path, e);
        }
    }

    private static void validateTransport(URL url, boolean allowInsecure) {
        if ("https".equalsIgnoreCase(url.getProtocol())) return;
        if (!"http".equalsIgnoreCase(url.getProtocol())) {
            throw new IllegalArgumentException("Only HTTP(S) sources are supported: " + url);
        }
        if (allowInsecure || isLoopback(url.getHost())) return;
        throw new IllegalArgumentException("Plain HTTP is only allowed for loopback unless allowInsecureHttp=true: " + url);
    }

    private static boolean isLoopback(String host) {
        if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host)) return true;
        try { return InetAddress.getByName(host).isLoopbackAddress(); }
        catch (Exception ignored) { return false; }
    }

    private static List<Map<String, String>> dimensionCombinations(Map<String, List<String>> dimensions) {
        List<Map<String, String>> result = new ArrayList<Map<String, String>>();
        result.add(new LinkedHashMap<String, String>());
        for (Map.Entry<String, List<String>> dimension : dimensions.entrySet()) {
            if (dimension.getValue() == null || dimension.getValue().isEmpty()) {
                throw new IllegalArgumentException("Dimension " + dimension.getKey() + " has no values");
            }
            List<Map<String, String>> next = new ArrayList<Map<String, String>>();
            for (Map<String, String> existing : result) {
                for (String value : dimension.getValue()) {
                    Map<String, String> expanded = new LinkedHashMap<String, String>(existing);
                    expanded.put(dimension.getKey(), value);
                    next.add(expanded);
                }
            }
            result = next;
        }
        return result;
    }

    private static <T> T require(Map<String, T> values, String key, String kind) {
        T value = values.get(key);
        if (value == null) throw new IllegalArgumentException("Unknown " + kind + " reference: " + key);
        return value;
    }
}
