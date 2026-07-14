package com.example.logconsole.model;

public final class ApplicationStatus {
    public enum State { GREEN, YELLOW, RED, GRAY }

    private final String applicationId;
    private final String displayName;
    private final String environment;
    private final int sources;
    private final int available;
    private final long totalBytes;
    private final State state;
    private final String detail;

    public ApplicationStatus(String applicationId, String displayName, String environment, int sources,
                             int available, long totalBytes, State state, String detail) {
        this.applicationId = applicationId;
        this.displayName = displayName;
        this.environment = environment;
        this.sources = sources;
        this.available = available;
        this.totalBytes = totalBytes;
        this.state = state;
        this.detail = detail;
    }

    public String getApplicationId() { return applicationId; }
    public String getDisplayName() { return displayName; }
    public String getEnvironment() { return environment; }
    public int getSources() { return sources; }
    public int getAvailable() { return available; }
    public long getTotalBytes() { return totalBytes; }
    public State getState() { return state; }
    public String getDetail() { return detail; }
}
