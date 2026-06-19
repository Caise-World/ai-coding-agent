package com.aicoding.agent.tool;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

@Component
public class FileReadTool implements Tool {

    private static final int MAX_LENGTH = 10000;

    @Override
    public String name() {
        return "FileReadTool";
    }

    @Override
    public String description() {
        return "Reads the content of a file. Input: absolute file path. Output: file content or error message.";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("name", "FileReadTool");
        schema.put("description", "Reads the content of a file");

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> inputProp = new HashMap<>();
        inputProp.put("type", "string");
        inputProp.put("description", "Absolute file path to read");
        properties.put("input", inputProp);

        schema.put("parameters", properties);
        return schema;
    }

    @Override
    public ToolResult execute(ToolContext context) {
        try {
            String input = context.getInput();
            Path path = Paths.get(input.trim());
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                return ToolResult.error("Error: File not found or is not a regular file: " + input);
            }
            String content = Files.readString(path);
            if (content.length() > MAX_LENGTH) {
                content = content.substring(0, MAX_LENGTH) + "\n... (truncated)";
            }
            return ToolResult.success(content);
        } catch (IOException e) {
            return ToolResult.error("Error reading file: " + e.getMessage());
        }
    }
}
