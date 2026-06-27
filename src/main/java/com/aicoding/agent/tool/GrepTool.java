package com.aicoding.agent.tool;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

@Component
public class GrepTool implements Tool {

    private static final int MAX_RESULTS = 30;

    @Override
    public String name() {
        return "GrepTool";
    }

    @Override
    public String description() {
        return "Searches files for a regex pattern using system grep. Input format: 'pattern' (searches /workspace) or 'path:pattern' to search a specific directory. Returns matching lines as path:line:text.";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("name", "GrepTool");
        schema.put("description", description());

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> inputProp = new HashMap<>();
        inputProp.put("type", "string");
        inputProp.put("description", "Search pattern, optionally prefixed with path:");
        properties.put("input", inputProp);

        schema.put("parameters", properties);
        return schema;
    }

    @Override
    public ToolResult execute(ToolContext context) {
        String input = context.getInput();
        if (input == null || input.isBlank()) {
            return ToolResult.error("Error: pattern is required");
        }

        String[] parts = splitPathAndPattern(input);
        String searchPath = parts[0];
        String pattern = parts[1];

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "grep", "-rn", "--include=*.java",
                    "--include=*.xml", "--include=*.yml", "--include=*.yaml",
                    "--include=*.md", "--include=*.properties",
                    "-E", pattern, searchPath
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringJoiner out = new StringJoiner("\n");
            int count = 0;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null && count < MAX_RESULTS) {
                    out.add(line);
                    count++;
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 1 && count == 0) {
                return ToolResult.success("No matches found for pattern: " + pattern);
            }
            if (count >= MAX_RESULTS) {
                out.add("... (truncated at " + MAX_RESULTS + " results)");
            }
            return ToolResult.success(out.toString());
        } catch (Exception e) {
            return ToolResult.error("Error running grep: " + e.getMessage());
        }
    }

    private String[] splitPathAndPattern(String input) {
        int colon = input.indexOf(':');
        if (colon <= 0) {
            return new String[]{"/workspace", input.trim()};
        }
        String left = input.substring(0, colon).trim();
        String right = input.substring(colon + 1).trim();
        if (right.isEmpty()) {
            return new String[]{"/workspace", input.trim()};
        }
        if (left.startsWith("/") || left.startsWith(".") || left.startsWith("~")) {
            return new String[]{left, right};
        }
        return new String[]{"/workspace/" + left, right};
    }
}