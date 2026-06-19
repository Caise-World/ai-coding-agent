package com.aicoding.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

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
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("name", "FileWriteTool");
        schema.put("description", "Writes content to a file");

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> inputProp = new HashMap<>();
        inputProp.put("type", "string");
        inputProp.put("description", "JSON with file_path and content, or 'filePath|content' format");
        properties.put("input", inputProp);

        schema.put("parameters", properties);
        return schema;
    }

    @Override
    public ToolResult execute(ToolContext context) {
        try {
            String input = context.getInput();
            String filePath;
            String content;

            if (input.trim().startsWith("{")) {
                JsonNode node = objectMapper.readTree(input);
                filePath = node.get("file_path").asText();
                content = node.get("content").asText();
            } else {
                String[] parts = input.split("\\|", 2);
                if (parts.length != 2) {
                    return ToolResult.error("Error: Input must be JSON with file_path/content or 'filePath|content' format");
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
            return ToolResult.success("Successfully wrote to file: " + filePath);
        } catch (IOException e) {
            return ToolResult.error("Error writing file: " + e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("Error parsing input: " + e.getMessage());
        }
    }
}
