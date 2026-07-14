package com.example.logconsole.http;

public final class RemoteMetadata {
    private final int statusCode;
    private final long length;
    private final String etag;
    private final long lastModified;
    private final boolean rangeSupported;

    public RemoteMetadata(int statusCode, long length, String etag, long lastModified, boolean rangeSupported) {
        this.statusCode = statusCode;
        this.length = length;
        this.etag = etag;
        this.lastModified = lastModified;
        this.rangeSupported = rangeSupported;
    }

    public int getStatusCode() { return statusCode; }
    public long getLength() { return length; }
    public String getEtag() { return etag; }
    public long getLastModified() { return lastModified; }
    public boolean isRangeSupported() { return rangeSupported; }
    public boolean isAvailable() { return statusCode >= 200 && statusCode < 300; }

    public String identity() {
        if (etag != null) return "etag:" + etag;
        return "modified:" + lastModified + ":length:" + length;
    }
}
