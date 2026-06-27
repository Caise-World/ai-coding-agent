package com.aicoding.agent.rag.store;

import com.aicoding.agent.rag.chunk.Chunk;

public record ScoredChunk(Chunk chunk, double score) {
}