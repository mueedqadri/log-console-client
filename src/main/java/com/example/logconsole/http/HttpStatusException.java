package com.example.logconsole.http;

import java.io.IOException;

public final class HttpStatusException extends IOException {
    private final int statusCode;

    public HttpStatusException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() { return statusCode; }
}
