package com.example.logconsole.http;

import com.example.logconsole.config.AppConfig;
import com.example.logconsole.config.SettingsStore;
import com.example.logconsole.config.UserSettings;
import org.jline.reader.LineReader;

import java.io.IOException;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CredentialManager implements AuthorizationProvider, AutoCloseable {
    private final AppConfig config;
    private final LineReader reader;
    private final SettingsStore store;
    private final UserSettings settings;
    private final Map<AppConfig.ConnectionConfig, String> ids = new IdentityHashMap<AppConfig.ConnectionConfig, String>();
    private final Map<String, AuthContext> contexts = new LinkedHashMap<String, AuthContext>();

    public CredentialManager(AppConfig config, LineReader reader, SettingsStore store, String cliUsername) throws IOException {
        this.config = config;
        this.reader = reader;
        this.store = store;
        this.settings = store.load();
        for (Map.Entry<String, AppConfig.ConnectionConfig> entry : config.connections.entrySet()) {
            ids.put(entry.getValue(), entry.getKey());
            initialize(entry.getKey(), entry.getValue(), cliUsername);
        }
        store.save(settings);
    }

    private void initialize(String id, AppConfig.ConnectionConfig connection, String cliUsername) {
        String username = cliUsername;
        if (blank(username)) username = settings.usernames.get(id);
        if (blank(username)) username = connection.defaultUsername;
        if (blank(username)) username = reader.readLine("Username for " + id + ": ").trim();
        char[] password = resolvePassword(id, connection);
        contexts.put(id, new AuthContext(username, password));
        Arrays.fill(password, '\0');
        settings.usernames.put(id, username);
    }

    private char[] resolvePassword(String id, AppConfig.ConnectionConfig connection) {
        if (!blank(connection.passwordEnvVar)) {
            String value = System.getenv(connection.passwordEnvVar);
            if (value != null && !value.isEmpty()) return value.toCharArray();
        }
        String value = reader.readLine("Password for " + id + ": ", '*');
        return value.toCharArray();
    }

    public void changeUsernames() throws IOException {
        for (Map.Entry<String, AppConfig.ConnectionConfig> entry : config.connections.entrySet()) {
            String id = entry.getKey();
            AuthContext current = contexts.get(id);
            String username = reader.readLine("Username for " + id + " [" + current.getUsername() + "]: ").trim();
            if (username.isEmpty()) username = current.getUsername();
            char[] password = resolvePassword(id, entry.getValue());
            current.close();
            contexts.put(id, new AuthContext(username, password));
            Arrays.fill(password, '\0');
            settings.usernames.put(id, username);
        }
        store.save(settings);
    }

    @Override public String header(AppConfig.ConnectionConfig connection) {
        String id = ids.get(connection);
        AuthContext context = contexts.get(id);
        if (context == null) throw new IllegalStateException("No credentials for connection " + id);
        return context.authorizationHeader();
    }

    @Override public void close() {
        for (AuthContext context : contexts.values()) context.close();
        contexts.clear();
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
