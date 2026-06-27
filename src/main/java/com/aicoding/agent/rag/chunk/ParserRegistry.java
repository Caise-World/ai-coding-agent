package com.aicoding.agent.rag.chunk;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class ParserRegistry {

    private final List<ChunkParser> parsers;

    public ParserRegistry(List<ChunkParser> parsers) {
        this.parsers = parsers;
    }

    public ChunkParser findFor(Path file) {
        return parsers.stream()
                .filter(p -> p.supports(file))
                .findFirst()
                .orElse(null);
    }
}