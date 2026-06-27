package com.aicoding.agent.rag.chunk;

import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class PropertiesChunkParser implements ChunkParser {

    @Override
    public boolean supports(Path file) {
        return file.toString().endsWith(".properties");
    }

    @Override
    public List<Chunk> parse(Path file) throws Exception {
        List<String> lines = Files.readAllLines(file);
        List<Chunk> chunks = new ArrayList<>();
        String path = file.toString();

        StringBuilder current = new StringBuilder();
        int startLine = 1;
        int lineCount = 0;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            lineCount++;
            if (line.isEmpty() || line.startsWith("#")) {
                if (current.length() > 0) {
                    chunks.add(new Chunk(path, startLine, i, "PROPERTIES", "block", current.toString().trim()));
                    current.setLength(0);
                }
                startLine = i + 2;
                continue;
            }
            if (current.length() == 0) startLine = i + 1;
            current.append(line).append("\n");
            if (current.length() > 4000) {
                chunks.add(new Chunk(path, startLine, i + 1, "PROPERTIES", "block", current.toString().trim()));
                current.setLength(0);
                startLine = i + 2;
            }
        }
        if (current.length() > 0) {
            chunks.add(new Chunk(path, startLine, lineCount, "PROPERTIES", "block", current.toString().trim()));
        }
        return chunks;
    }
}