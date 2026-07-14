package com.example.logconsole.http;

import com.example.logconsole.config.AppConfig;

public interface AuthorizationProvider {
    String header(AppConfig.ConnectionConfig connection);
}
