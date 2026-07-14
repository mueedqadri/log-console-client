package com.example.logconsole.http;

import com.example.logconsole.config.AppConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public final class RangeHttpClient {
    private final AuthorizationProvider authorization;
    private final boolean debug;

    public RangeHttpClient(AuthContext auth, boolean debug) {
        this(new FixedAuthorizationProvider(auth), debug);
    }

    public RangeHttpClient(AuthorizationProvider authorization, boolean debug) {
        this.authorization = authorization;
        this.debug = debug;
    }

    public RemoteMetadata metadata(URL url, AppConfig.ConnectionConfig config) throws IOException {
        HttpURLConnection connection = open(url, config, "HEAD");
        try {
            int code = connection.getResponseCode();
            if (code == 405 || code == 501) return metadataViaRange(url, config);
            if (code < 200 || code >= 300) return new RemoteMetadata(code, -1, header(connection, "ETag"),
                    connection.getLastModified(), false);
            return fromHeaders(connection, code, contentLength(connection),
                    "bytes".equalsIgnoreCase(header(connection, "Accept-Ranges")));
        } finally { connection.disconnect(); }
    }

    private RemoteMetadata metadataViaRange(URL url, AppConfig.ConnectionConfig config) throws IOException {
        HttpURLConnection connection = open(url, config, "GET");
        connection.setRequestProperty("Range", "bytes=0-0");
        try {
            int code = connection.getResponseCode();
            if (code == 206) {
                long length = totalLength(connection, -1);
                if (length < 0) {
                    throw new IOException("Partial response missing a valid Content-Range for " + safe(url));
                }
                drain(connection); // A valid metadata probe has at most one byte.
                return fromHeaders(connection, code, length, true);
            }
            if (code == 416 && totalLength(connection, -1) == 0) {
                // RFC 9110: an empty representation answers bytes=0-0 with "bytes */0".
                return fromHeaders(connection, 200, 0, true);
            }
            if (code == 200) {
                // The server ignored Range. Do not drain a potentially large response.
                return fromHeaders(connection, code, contentLength(connection), false);
            }
            drain(connection);
            return fromHeaders(connection, code, -1, false);
        } finally { connection.disconnect(); }
    }

    public RangeResponse getRange(URL url, AppConfig.ConnectionConfig config, long start, long end,
                                  String ifRange, boolean requireRange) throws IOException {
        if (start < 0 || end < start) throw new IllegalArgumentException("Invalid byte range " + start + "-" + end);
        IOException last = null;
        for (int attempt = 0; attempt <= config.retries; attempt++) {
            HttpURLConnection connection = open(url, config, "GET");
            connection.setRequestProperty("Range", "bytes=" + start + "-" + end);
            if (ifRange != null && !ifRange.isEmpty()) connection.setRequestProperty("If-Range", ifRange);
            try {
                int code = connection.getResponseCode();
                if (code == 401 || code == 403 || code == 404 || code == 416) {
                    throw new HttpStatusException(code, "HTTP " + code + " for " + safe(url));
                }
                if (code == 429 || code >= 500) {
                    last = new IOException("Temporary HTTP " + code + " for " + safe(url));
                } else if (code == 206 || (!requireRange && code == 200 && start == 0)) {
                    byte[] bytes = readAll(connection.getInputStream());
                    long total = totalLength(connection, contentLength(connection));
                    RemoteMetadata metadata = fromHeaders(connection, code, total, code == 206);
                    return new RangeResponse(bytes, metadata, start, start + bytes.length - 1L);
                } else if (code == 200 && requireRange) {
                    drain(connection);
                    throw new IOException("Server ignored Range request for " + safe(url));
                } else {
                    throw new HttpStatusException(code, "Unexpected HTTP " + code + " for " + safe(url));
                }
            } catch (IOException e) {
                last = e;
                if (e instanceof HttpStatusException) throw e;
            } finally { connection.disconnect(); }
            if (attempt < config.retries) backoff(attempt);
        }
        throw last == null ? new IOException("Range request failed for " + safe(url)) : last;
    }

    private HttpURLConnection open(URL url, AppConfig.ConnectionConfig config, String method) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(config.connectTimeoutMillis);
        connection.setReadTimeout(config.readTimeoutMillis);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept-Encoding", "identity");
        connection.setRequestProperty("Authorization", authorization.header(config));
        connection.setRequestProperty("User-Agent", "log-console/1.0");
        if (debug) System.err.println("HTTP " + method + " " + safe(url));
        return connection;
    }

    private static RemoteMetadata fromHeaders(HttpURLConnection c, int code, long length, boolean ranges) {
        return new RemoteMetadata(code, length, header(c, "ETag"), c.getLastModified(), ranges);
    }

    private static long contentLength(HttpURLConnection connection) {
        String value = header(connection, "Content-Length");
        if (value == null) return -1;
        try { return Long.parseLong(value); }
        catch (NumberFormatException ignored) { return -1; }
    }

    private static long totalLength(HttpURLConnection connection, long fallback) {
        String value = header(connection, "Content-Range");
        if (value != null) {
            int slash = value.lastIndexOf('/');
            if (slash >= 0 && slash + 1 < value.length()) {
                try { return Long.parseLong(value.substring(slash + 1)); }
                catch (NumberFormatException ignored) { }
            }
        }
        return fallback;
    }

    private static String header(HttpURLConnection connection, String name) {
        return connection.getHeaderField(name);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) out.write(buffer, 0, read);
            return out.toByteArray();
        } finally { in.close(); }
    }

    private static void drain(HttpURLConnection connection) {
        InputStream input = connection.getErrorStream();
        if (input == null) {
            try { input = connection.getInputStream(); }
            catch (IOException ignored) { return; }
        }
        try {
            byte[] buffer = new byte[1024];
            while (input.read(buffer) >= 0) { }
        } catch (IOException ignored) { }
        finally { try { input.close(); } catch (IOException ignored) { } }
    }

    private static void backoff(int attempt) throws IOException {
        try { Thread.sleep(Math.min(2000L, 200L * (1L << attempt))); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Interrupted", e); }
    }

    private static String safe(URL url) {
        return url.getProtocol().toLowerCase(Locale.ROOT) + "://" + url.getAuthority() + url.getPath();
    }

    private static final class FixedAuthorizationProvider implements AuthorizationProvider {
        private final AuthContext auth;
        FixedAuthorizationProvider(AuthContext auth) { this.auth = auth; }
        @Override public String header(AppConfig.ConnectionConfig connection) { return auth.authorizationHeader(); }
    }
}
