package com.aicoding.agent.dto;

public class WorkspaceResponse {
    private String path;
    private int chunksCreated;
    private long indexTimeMs;
    private boolean cacheHit;
    private String error;

    public WorkspaceResponse() {}

    public static WorkspaceResponse error(String message) {
        WorkspaceResponse r = new WorkspaceResponse();
        r.error = message;
        return r;
    }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public int getChunksCreated() { return chunksCreated; }
    public void setChunksCreated(int c) { this.chunksCreated = c; }
    public long getIndexTimeMs() { return indexTimeMs; }
    public void setIndexTimeMs(long t) { this.indexTimeMs = t; }
    public boolean isCacheHit() { return cacheHit; }
    public void setCacheHit(boolean h) { this.cacheHit = h; }
    public String getError() { return error; }
    public void setError(String e) { this.error = e; }
}