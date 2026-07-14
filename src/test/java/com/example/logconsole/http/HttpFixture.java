package com.example.logconsole.http;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public final class HttpFixture implements AutoCloseable {
    final HttpServer server;
    public final AtomicInteger rangeGets = new AtomicInteger();
    public volatile byte[] content;
    volatile String etag = "\"v1\"";

    public HttpFixture(String text) throws IOException {
        content = text.getBytes(StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/test.log", new Handler(true));
        server.createContext("/head-unsupported.log", new Handler(true, false));
        server.createContext("/ignore-range.log", new Handler(false));
        server.start();
    }

    public String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/"; }

    public void append(String value) {
        byte[] suffix = value.getBytes(StandardCharsets.UTF_8);
        byte[] updated = Arrays.copyOf(content, content.length + suffix.length);
        System.arraycopy(suffix, 0, updated, content.length, suffix.length);
        content = updated;
        etag = "\"v" + content.length + "\"";
    }

    @Override public void close() { server.stop(0); }

    private final class Handler implements HttpHandler {
        private final boolean ranges;
        private final boolean headSupported;

        Handler(boolean ranges) { this(ranges, true); }
        Handler(boolean ranges, boolean headSupported) {
            this.ranges = ranges;
            this.headSupported = headSupported;
        }

        @Override public void handle(HttpExchange exchange) throws IOException {
            if (!"Basic dXNlcjpwYXNz".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                respond(exchange, 401, new byte[0], null);
                return;
            }
            Headers headers = exchange.getResponseHeaders();
            headers.set("Accept-Ranges", "bytes");
            headers.set("ETag", etag);
            headers.set("Last-Modified", "Mon, 13 Jul 2026 12:00:00 GMT");
            if ("HEAD".equals(exchange.getRequestMethod())) {
                if (!headSupported) {
                    respond(exchange, 405, new byte[0], null);
                    return;
                }
                headers.set("Content-Length", String.valueOf(content.length));
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
                return;
            }
            String range = exchange.getRequestHeaders().getFirst("Range");
            if (!ranges || range == null) {
                respond(exchange, 200, content, null);
                return;
            }
            rangeGets.incrementAndGet();
            String[] values = range.substring("bytes=".length()).split("-", -1);
            int start = Integer.parseInt(values[0]);
            int end = values[1].isEmpty() ? content.length - 1 : Math.min(content.length - 1, Integer.parseInt(values[1]));
            if (start >= content.length) {
                headers.set("Content-Range", "bytes */" + content.length);
                respond(exchange, 416, new byte[0], null);
                return;
            }
            byte[] body = Arrays.copyOfRange(content, start, end + 1);
            respond(exchange, 206, body, "bytes " + start + "-" + end + "/" + content.length);
        }

        private void respond(HttpExchange exchange, int status, byte[] body, String contentRange) throws IOException {
            if (contentRange != null) exchange.getResponseHeaders().set("Content-Range", contentRange);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }
    }
}
