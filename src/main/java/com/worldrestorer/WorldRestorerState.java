package com.worldrestorer;

public class WorldRestorerState {
    private String lastExtractTime;
    private String lastExtractStatus;
    private String lastExtractDetails;
    private int lastExtractFiles;
    private long lastExtractDurationMs;
    private String lastResetTime;
    private String lastResetStatus;
    private String lastResetDetails;
    private long lastResetDurationMs;

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

    public String getLastResetTime() {
        return lastResetTime;
    }

    public void setLastResetTime(String lastResetTime) {
        this.lastResetTime = lastResetTime;
    }

    public String getLastResetStatus() {
        return lastResetStatus;
    }

    public void setLastResetStatus(String lastResetStatus) {
        this.lastResetStatus = lastResetStatus;
    }

    public String getLastResetDetails() {
        return lastResetDetails;
    }

    public void setLastResetDetails(String lastResetDetails) {
        this.lastResetDetails = lastResetDetails;
    }

    public long getLastResetDurationMs() {
        return lastResetDurationMs;
    }

    public void setLastResetDurationMs(long lastResetDurationMs) {
        this.lastResetDurationMs = lastResetDurationMs;
    }
}
