package com.aicoding.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;

@Component
public class FileWriteTool implements Tool {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "FileWriteTool";
    }

    @Override
    public String description() {
        return "Writes content to a file. Input: JSON with file_path and content fields, or pipe-separated 'filePath|content'.";
    }

    @Override
    public String execute(String input) {
        try {
            String filePath;
            String content;

            // Try JSON format first
            if (input.trim().startsWith("{")) {
                JsonNode node = objectMapper.readTree(input);
                filePath = node.get("file_path").asText();
                content = node.get("content").asText();
            } else {
                // Fall back to pipe-separated format
                String[] parts = input.split("\\|", 2);
                if (parts.length != 2) {
                    return "Error: Input must be JSON with file_path/content or 'filePath|content' format";
                }
                filePath = parts[0].trim();
                content = parts[1];
            }

            Path path = Paths.get(filePath);
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            Files.writeString(path, content);
            return "Successfully wrote to file: " + filePath;
        } catch (IOException e) {
            return "Error writing file: " + e.getMessage();
        } catch (Exception e) {
            return "Error parsing input: " + e.getMessage();
        }
    }
}
