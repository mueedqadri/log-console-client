package com.example.logconsole.download;

import com.example.logconsole.http.RemoteMetadata;
import com.example.logconsole.model.ExpandedSource;

import java.nio.file.Path;

public final class StagedSource {
    private final ExpandedSource source;
    private final Path path;
    private final RemoteMetadata metadata;

    public StagedSource(ExpandedSource source, Path path, RemoteMetadata metadata) {
        this.source = source;
        this.path = path;
        this.metadata = metadata;
    }

    public ExpandedSource getSource() { return source; }
    public Path getPath() { return path; }
    public RemoteMetadata getMetadata() { return metadata; }
}
