package com.aicoding.agent.rag.store;

import com.aicoding.agent.rag.chunk.Chunk;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface VectorStore {

    void add(Chunk chunk, float[] embedding);

    List<ScoredChunk> topK(float[] queryVec, int k);

    List<ScoredChunk> topK(float[] queryVec, int k, double minScore);

    int size();

    void clear();

    void save(Path cacheDir) throws IOException;

    void load(Path cacheDir) throws IOException;
}