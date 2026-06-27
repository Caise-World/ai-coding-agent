package com.aicoding.agent.rag.chunk;

import java.nio.file.Path;
import java.util.List;

public interface ChunkParser {
    boolean supports(Path file);

    List<Chunk> parse(Path file) throws Exception;
}