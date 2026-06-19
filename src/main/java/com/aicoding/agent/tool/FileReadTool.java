package com.aicoding.agent.tool;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;

@Component
public class FileReadTool implements Tool {

    @Override
    public String name() {
        return "FileReadTool";
    }

    @Override
    public String description() {
        return "Reads the content of a file. Input: absolute file path. Output: file content or error message.";
    }

    @Override
    public String execute(String input) {
        try {
            Path path = Paths.get(input.trim());
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                return "Error: File not found or is not a regular file: " + input;
            }
            String content = Files.readString(path);
            int maxLength = 10000;
            if (content.length() > maxLength) {
                content = content.substring(0, maxLength) + "\n... (truncated)";
            }
            return content;
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }
}
