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
                You are a tool selector. Pick ONE tool or NONE.

                Available tools:
                %s

                Examples:
                "列出所有文件" → ProjectScanTool
                "项目结构是什么" → ProjectScanTool
                "读取 pom.xml" → FileReadTool
                "查看 CLAUDE.md 的内容" → FileReadTool
                "查看 AgentApplication.java 的源代码" → FileReadTool
                "帮我创建一个 HelloWorld.java" → FileWriteTool
                "write a README.md file" → FileWriteTool
                "write a README.md file for this project" → FileWriteTool
                "生成一个 application.properties" → FileWriteTool
                "执行 mvn clean compile" → CommandExecuteTool
                "run git status" → CommandExecuteTool
                "你好，你是谁" → NONE
                "什么是 Spring Boot" → NONE

                Now classify this:
                User message: %s
                Project path: %s

                Output ONLY: {"toolName": "ToolName", "input": "the input"}
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
