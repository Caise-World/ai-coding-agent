package com.aicoding.agent.tool;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

@Component
public class ProjectScanTool implements Tool {

    private static final int MAX_DEPTH = 4;
    private static final int MAX_FILES = 50;

    @Override
    public String name() {
        return "ProjectScanTool";
    }

    @Override
    public String description() {
        return "Scans a project directory and returns its structure. Input: absolute directory path. Output: file tree with key files listed.";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("name", "ProjectScanTool");
        schema.put("description", "Scans a project directory and returns its structure");

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> inputProp = new HashMap<>();
        inputProp.put("type", "string");
        inputProp.put("description", "Absolute directory path to scan");
        properties.put("input", inputProp);

        schema.put("parameters", properties);
        return schema;
    }

    @Override
    public ToolResult execute(ToolContext context) {
        try {
            String input = context.getInput();
            Path path = Paths.get(input.trim());
            if (!Files.exists(path) || !Files.isDirectory(path)) {
                return ToolResult.error("Error: Directory not found: " + input);
            }

            StringJoiner result = new StringJoiner("\n");
            result.add("Project Structure:");
            result.add("=".repeat(50));

            ScanState state = new ScanState();

            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (state.fileCount >= MAX_FILES) {
                        return FileVisitResult.TERMINATE;
                    }
                    if (state.depth <= MAX_DEPTH) {
                        String name = file.getFileName().toString();
                        if (isRelevantFile(name)) {
                            result.add("  ".repeat(state.depth) + name);
                            state.fileCount++;
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (state.depth <= MAX_DEPTH) {
                        String name = dir.getFileName().toString();
                        if (!name.startsWith(".") && !name.equals("target") && !name.equals("node_modules")) {
                            result.add("  ".repeat(state.depth) + "[DIR] " + name);
                            state.depth++;
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    if (state.depth > 0) {
                        state.depth--;
                    }
                    return FileVisitResult.CONTINUE;
                }

                private boolean isRelevantFile(String name) {
                    return name.endsWith(".java") ||
                           name.endsWith(".xml") ||
                           name.endsWith(".yml") ||
                           name.endsWith(".yaml") ||
                           name.endsWith(".properties") ||
                           name.endsWith(".md") ||
                           name.equals("pom.xml") ||
                           name.equals("build.gradle") ||
                           name.equals("settings.gradle");
                }
            });

            if (state.fileCount >= MAX_FILES) {
                result.add("\n... (showing first " + MAX_FILES + " relevant files)");
            }

            return ToolResult.success(result.toString());
        } catch (IOException e) {
            return ToolResult.error("Error scanning directory: " + e.getMessage());
        }
    }

    private static class ScanState {
        int depth = 0;
        int fileCount = 0;
    }
}
