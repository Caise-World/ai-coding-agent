package com.aicoding.agent.rag.chunk;

public record Chunk(
        String path,
        int startLine,
        int endLine,
        String kind,
        String symbol,
        String content
) {
    public String location() {
        return path + ":" + startLine + "-" + endLine;
    }
}