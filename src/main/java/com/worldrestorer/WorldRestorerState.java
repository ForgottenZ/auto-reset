package com.worldrestorer;

public class WorldRestorerState {
    private String lastExtractTime;
    private String lastExtractStatus;
    private String lastExtractDetails;
    private int lastExtractFiles;
    private long lastExtractDurationMs;

    public String getLastExtractTime() {
        return lastExtractTime;
    }

    public void setLastExtractTime(String lastExtractTime) {
        this.lastExtractTime = lastExtractTime;
    }

    public String getLastExtractStatus() {
        return lastExtractStatus;
    }

    public void setLastExtractStatus(String lastExtractStatus) {
        this.lastExtractStatus = lastExtractStatus;
    }

    public String getLastExtractDetails() {
        return lastExtractDetails;
    }

    public void setLastExtractDetails(String lastExtractDetails) {
        this.lastExtractDetails = lastExtractDetails;
    }

    public int getLastExtractFiles() {
        return lastExtractFiles;
    }

    public void setLastExtractFiles(int lastExtractFiles) {
        this.lastExtractFiles = lastExtractFiles;
    }

    public long getLastExtractDurationMs() {
        return lastExtractDurationMs;
    }

    public void setLastExtractDurationMs(long lastExtractDurationMs) {
        this.lastExtractDurationMs = lastExtractDurationMs;
    }
}
