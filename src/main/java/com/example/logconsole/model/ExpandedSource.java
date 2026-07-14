package com.example.logconsole.model;

import com.example.logconsole.config.AppConfig;

import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ExpandedSource {
    private final String applicationId;
    private final String applicationName;
    private final String environmentId;
    private final String locationId;
    private final Map<String, String> dimensions;
    private final String label;
    private final URL url;
    private final AppConfig.ConnectionConfig connection;
    private final AppConfig.ParserConfig parser;
    private final String timezone;

    public ExpandedSource(String applicationId, String applicationName, String environmentId,
                          String locationId, Map<String, String> dimensions, String label, URL url,
                          AppConfig.ConnectionConfig connection, AppConfig.ParserConfig parser, String timezone) {
        this.applicationId = applicationId;
        this.applicationName = applicationName;
        this.environmentId = environmentId;
        this.locationId = locationId;
        this.dimensions = Collections.unmodifiableMap(new LinkedHashMap<String, String>(dimensions));
        this.label = label;
        this.url = url;
        this.connection = connection;
        this.parser = parser;
        this.timezone = timezone;
    }

    public String getApplicationId() { return applicationId; }
    public String getApplicationName() { return applicationName; }
    public String getEnvironmentId() { return environmentId; }
    public String getLocationId() { return locationId; }
    public Map<String, String> getDimensions() { return dimensions; }
    public String getLabel() { return label; }
    public URL getUrl() { return url; }
    public AppConfig.ConnectionConfig getConnection() { return connection; }
    public AppConfig.ParserConfig getParser() { return parser; }
    public String getTimezone() { return timezone; }

    public String stableKey() {
        StringBuilder value = new StringBuilder(environmentId).append('/').append(locationId);
        for (Map.Entry<String, String> entry : dimensions.entrySet()) {
            value.append('/').append(entry.getKey()).append('=').append(entry.getValue());
        }
        return value.toString();
    }
}
