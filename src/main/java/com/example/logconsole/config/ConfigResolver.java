package com.example.logconsole.config;

public final class ConfigResolver {
    private ConfigResolver() { }

    public static AppConfig.ThresholdsConfig thresholds(AppConfig config, AppConfig.ApplicationConfig app) {
        if (app.thresholds != null) return app.thresholds;
        AppConfig.EnvironmentConfig environment = config.environments.get(app.environment);
        return environment != null && environment.status != null ? environment.status : config.defaults.status;
    }

    public static AppConfig.StreamConfig streaming(AppConfig config, AppConfig.ApplicationConfig app) {
        if (app.streaming != null) return app.streaming;
        AppConfig.EnvironmentConfig environment = config.environments.get(app.environment);
        return environment != null && environment.streaming != null ? environment.streaming : config.defaults.streaming;
    }

    public static AppConfig.DownloadConfig download(AppConfig config, AppConfig.ApplicationConfig app) {
        if (app.download != null) return app.download;
        AppConfig.EnvironmentConfig environment = config.environments.get(app.environment);
        return environment != null && environment.download != null ? environment.download : config.defaults.download;
    }
}
