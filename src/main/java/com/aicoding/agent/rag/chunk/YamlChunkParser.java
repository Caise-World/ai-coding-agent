package com.aicoding.agent.rag.chunk;

import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class YamlChunkParser implements ChunkParser {

    private static final int MAX_CHARS_PER_CHUNK = 8000;

    @Override
    public boolean supports(Path file) {
        String name = file.toString();
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    @Override
    public List<Chunk> parse(Path file) throws Exception {
        String content = Files.readString(file);
        String name = file.getFileName().toString();
        return List.of(new Chunk(
                file.toString(),
                1,
                (int) content.lines().count(),
                "YAML",
                name,
                content.length() <= MAX_CHARS_PER_CHUNK ? content : content.substring(0, MAX_CHARS_PER_CHUNK)
        ));
    }
}