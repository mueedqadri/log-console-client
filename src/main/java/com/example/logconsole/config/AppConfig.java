package com.example.logconsole.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AppConfig {
    public int schemaVersion = 1;
    public Map<String, ConnectionConfig> connections = new LinkedHashMap<String, ConnectionConfig>();
    public Map<String, EnvironmentConfig> environments = new LinkedHashMap<String, EnvironmentConfig>();
    public Map<String, LocationConfig> locations = new LinkedHashMap<String, LocationConfig>();
    public Map<String, ParserConfig> parsers = new LinkedHashMap<String, ParserConfig>();
    public Map<String, ApplicationConfig> applications = new LinkedHashMap<String, ApplicationConfig>();
    public DefaultsConfig defaults = new DefaultsConfig();

    public static final class ConnectionConfig {
        public String baseUrl;
        public String defaultUsername;
        public String passwordEnvVar;
        public boolean allowInsecureHttp;
        public int connectTimeoutMillis = 5000;
        public int readTimeoutMillis = 15000;
        public int retries = 2;
        public int concurrency = 6;
    }

    public static final class EnvironmentConfig {
        public String displayName;
        public String connection;
        public String timezone = "UTC";
        public ThresholdsConfig status;
        public StreamConfig streaming;
        public DownloadConfig download;
    }

    public static final class LocationConfig {
        public String displayName;
        public Map<String, String> properties = new LinkedHashMap<String, String>();
    }

    public static final class ParserConfig {
        public String headerRegex;
        public String timestampPattern = "yyyy-MM-dd HH:mm:ss,SSS";
        public String timezone;
        public String timestampGroup = "timestamp";
        public String levelGroup = "level";
        public String loggerGroup = "logger";
        public String messageGroup = "message";
        public List<String> levels = new ArrayList<String>(Arrays.asList("INFO", "DEBUG", "WARN", "ERROR"));
    }

    public static final class ApplicationConfig {
        public String displayName;
        public String application;
        public String environment;
        public String parser;
        public List<String> locations = new ArrayList<String>();
        public Map<String, List<String>> dimensions = new LinkedHashMap<String, List<String>>();
        public String currentPathTemplate;
        public String archivePathTemplate;
        public String archiveDatePattern = "yyyy-MM-dd";
        public String sourceLabelTemplate = "{environment}/{location.id}";
        public String outputFileTemplate = "{application}.combined.{date}.log";
        public String outputDatePattern = "yyyy-MM-dd";
        public ThresholdsConfig thresholds;
        public StreamConfig streaming;
        public DownloadConfig download;
    }

    public static final class DefaultsConfig {
        public ThresholdsConfig status = new ThresholdsConfig();
        public StreamConfig streaming = new StreamConfig();
        public DownloadConfig download = new DownloadConfig();
    }

    public static final class ThresholdsConfig {
        public long warningBytes = 52428800L;
        public long criticalBytes = 104857600L;
    }

    public static final class StreamConfig {
        public int chunkBytes = 65536;
        public int initialChunks = 4;
        public int maxBufferedLines = 10000;
        public int pollMillis = 1000;
    }

    public static final class DownloadConfig {
        public String root = "downloads";
        public int rangeChunkBytes = 4194304;
    }
}
