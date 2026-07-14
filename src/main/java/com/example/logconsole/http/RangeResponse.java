package com.example.logconsole.http;

public final class RangeResponse {
    private final byte[] bytes;
    private final RemoteMetadata metadata;
    private final long start;
    private final long end;

    public RangeResponse(byte[] bytes, RemoteMetadata metadata, long start, long end) {
        this.bytes = bytes;
        this.metadata = metadata;
        this.start = start;
        this.end = end;
    }

    public byte[] getBytes() { return bytes; }
    public RemoteMetadata getMetadata() { return metadata; }
    public long getStart() { return start; }
    public long getEnd() { return end; }
}
