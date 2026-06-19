package com.aicoding.agent.registry;

import com.aicoding.agent.service.LLMService;
import com.aicoding.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ToolSelector {

    private final LLMService llmService;
    private final ToolRegistry toolRegistry;
    private final Pattern TOOL_CALL_PATTERN = Pattern.compile(
            "[\"']?(\\w+Tool)[\"']?\\s*[,:]?\\s*[\"']?([^\"'\\n]+)?[\"']?",
            Pattern.CASE_INSENSITIVE
    );

    public ToolSelector(LLMService llmService, ToolRegistry toolRegistry) {
        this.llmService = llmService;
        this.toolRegistry = toolRegistry;
    }

    public ToolSelection select(String userMessage, String projectPath) {
        List<Tool> tools = toolRegistry.getAll();

        String prompt = buildSelectionPrompt(userMessage, projectPath, tools);
        LLMService.LLMResponse response = llmService.chat(prompt);

        if (response.error() != null) {
            return new ToolSelection(null, null, "LLM error: " + response.error());
        }

        return parseToolSelection(response.content(), tools, projectPath);
    }

    private String buildSelectionPrompt(String userMessage, String projectPath, List<Tool> tools) {
        StringBuilder toolList = new StringBuilder();
        for (Tool tool : tools) {
            toolList.append("- ").append(tool.name()).append(": ").append(tool.description()).append("\n");
        }

        return """
                You are a tool selector. Given a user message, select the most appropriate tool.

                Available tools:
                %s

                User message: %s
                Project path: %s

                Respond ONLY with a JSON object in this format:
                {"toolName": "ToolName", "input": "the input to pass to the tool"}

                Rules:
                - Select ONLY one tool
                - The input should be relevant to the user's request
                - Use the project path as the base for file/directory inputs
                - If no tool is needed, respond with: {"toolName": "NONE", "input": ""}
                - Output ONLY the JSON object, no other text
                """.formatted(toolList.toString(), userMessage, projectPath);
    }

    private ToolSelection parseToolSelection(String content, List<Tool> tools, String projectPath) {
        if (content == null || content.isBlank()) {
            return new ToolSelection(null, null, "Empty response");
        }

        String toolName = null;
        String input = projectPath; // Default to project path

        // Try to find tool name
        for (Tool tool : tools) {
            String pattern = "\"" + tool.name() + "\"";
            if (content.contains(pattern) || content.toLowerCase().contains(tool.name().toLowerCase())) {
                toolName = tool.name();
                break;
            }
        }

        if (toolName == null) {
            // Try pattern matching
            Matcher matcher = TOOL_CALL_PATTERN.matcher(content);
            if (matcher.find()) {
                String matched = matcher.group(1);
                for (Tool tool : tools) {
                    if (matched.equalsIgnoreCase(tool.name())) {
                        toolName = tool.name();
                        break;
                    }
                }
            }
        }

        // Try to extract input from content
        if (toolName != null) {
            // Look for input field in JSON
            int inputIdx = content.indexOf("\"input\"");
            if (inputIdx >= 0) {
                int colon = content.indexOf(":", inputIdx);
                int startQuote = content.indexOf("\"", colon + 1);
                int endQuote = content.indexOf("\"", startQuote + 1);
                if (colon > 0 && startQuote > 0 && endQuote > startQuote) {
                    String extractedInput = content.substring(startQuote + 1, endQuote).trim();
                    if (!extractedInput.isEmpty() && !extractedInput.equals("null")) {
                        input = extractedInput;
                    }
                }
            }

            // If input still empty, try to extract from user message (path mentions)
            if (input == null || input.isEmpty() || input.equals(projectPath)) {
                // Try to find a path in the content
                Pattern pathPattern = Pattern.compile("(/[\\w\\-./]+)");
                Matcher pathMatcher = pathPattern.matcher(content);
                if (pathMatcher.find()) {
                    input = pathMatcher.group(1);
                }
            }
        }

        // Check for NONE
        if (toolName == null && content.toLowerCase().contains("none")) {
            return new ToolSelection("NONE", "", null);
        }

        if (toolName == null) {
            return new ToolSelection(null, null, "Could not determine tool from response: " + content.substring(0, Math.min(100, content.length())));
        }

        return new ToolSelection(toolName, input, null);
    }

    public record ToolSelection(String toolName, String input, String error) {
        public boolean isNone() {
            return "NONE".equals(toolName);
        }
        public boolean hasError() {
            return error != null;
        }
    }
}
