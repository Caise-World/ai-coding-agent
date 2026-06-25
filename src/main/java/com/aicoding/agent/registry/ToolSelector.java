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
            return new ToolSelection(null, null, false, "LLM error: " + response.error());
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

                Tool decision rules (in priority order):
                1. If the message is a greeting, general question, or conceptual explanation → NONE
                2. If the message asks to WRITE or CREATE a file → FileWriteTool
                3. If the message asks to READ or VIEW a specific file → FileReadTool
                4. If the message asks to SEARCH/FIND code matching a pattern → GrepTool
                5. If the message asks to RUN or EXECUTE a command → CommandExecuteTool
                6. If the message asks about project structure / listing files → ProjectScanTool
                7. Otherwise → NONE

                Available tools:
                %s

                Examples:
                "列出所有文件" → ProjectScanTool
                "项目结构是什么" → ProjectScanTool
                "读取 pom.xml" → FileReadTool
                "查看 AgentApplication.java 的源代码" → FileReadTool
                "帮我创建一个 HelloWorld.java" → FileWriteTool
                "执行 mvn clean compile" → CommandExecuteTool
                "搜索包含 ToolSelector 的 java 文件" → GrepTool
                "PluginBasedStreamingAgentService 怎么实现自愈" → NONE
                "MemoryService 怎么存长期记忆" → NONE
                "Java 和 Python 有什么区别" → NONE
                "你好，你是谁" → NONE
                "什么是 Spring Boot" → NONE
                "谢谢你的帮助" → NONE

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
        String input = projectPath;

        for (Tool tool : tools) {
            String pattern = "\"" + tool.name() + "\"";
            if (content.contains(pattern) || content.toLowerCase().contains(tool.name().toLowerCase())) {
                toolName = tool.name();
                break;
            }
        }

        if (toolName == null) {
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

        if (toolName != null) {
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

            if (input == null || input.isEmpty() || input.equals(projectPath)) {
                Pattern pathPattern = Pattern.compile("(/[\\w\\-./]+)");
                Matcher pathMatcher = pathPattern.matcher(content);
                if (pathMatcher.find()) {
                    input = pathMatcher.group(1);
                }
            }
        }

        if (toolName == null && content.toLowerCase().contains("none")) {
            return new ToolSelection("NONE", "", null);
        }

        if (toolName == null) {
            return new ToolSelection(null, null,
                    "Could not determine tool from response: " + content.substring(0, Math.min(100, content.length())));
        }

        return new ToolSelection(toolName, input, null);
    }

    public record ToolSelection(String toolName, String input, boolean needsRag, String error) {
        public ToolSelection(String toolName, String input, String error) {
            this(toolName, input, false, error);
        }
        public boolean isNone() {
            return "NONE".equals(toolName);
        }
        public boolean hasError() {
            return error != null;
        }
    }
}
