package com.example.logconsole.http;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

public final class AuthContext implements AutoCloseable {
    private final String username;
    private final char[] password;

    public AuthContext(String username, char[] password) {
        if (username == null || username.trim().isEmpty()) throw new IllegalArgumentException("Username is required");
        this.username = username;
        this.password = password == null ? new char[0] : password.clone();
    }

    public String getUsername() { return username; }

    public String authorizationHeader() {
        char[] combined = new char[username.length() + 1 + password.length];
        username.getChars(0, username.length(), combined, 0);
        combined[username.length()] = ':';
        System.arraycopy(password, 0, combined, username.length() + 1, password.length);
        byte[] bytes = new String(combined).getBytes(StandardCharsets.UTF_8);
        Arrays.fill(combined, '\0');
        try { return "Basic " + Base64.getEncoder().encodeToString(bytes); }
        finally { Arrays.fill(bytes, (byte) 0); }
    }

    @Override public void close() { Arrays.fill(password, '\0'); }
}
