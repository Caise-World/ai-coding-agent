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

                Apply the rules IN ORDER. STOP at the first match.

                RULE 1 — Greeting, thanks, or farewell → NONE
                  Match: 你好 / 谢谢 / 再见 / hello / hi / thanks / bye / 你是谁
                  Examples:
                    "你好，你是谁？" → NONE
                    "hello, how are you doing today?" → NONE
                    "谢谢你的帮助" → NONE

                RULE 2 — READ a specific file (must include a filename WITH extension) → FileReadTool
                  Match: 读取 / 查看 / 打开 / 显示 / read / view + filename ending in .java / .yml / .yaml / .xml / .md / .properties / .json / .txt
                  Examples:
                    "读取 pom.xml 文件的内容" → FileReadTool
                    "read the file CLAUDE.md" → FileReadTool
                    "帮我看下 application.yml 里配置了什么" → FileReadTool
                    "查看 AgentApplication.java 的源代码" → FileReadTool

                RULE 3 — RUN a system command → CommandExecuteTool
                  Match: a recognizable system command name (git / mvn / npm / yarn / docker / kubectl / curl / wget / ps / ls / top / ssh / scp / rsync / brew / apt / yum / pip / gradle), OR 执行 / 运行 / 跑 / run + a shell command
                  This rule fires ONLY on explicit command execution. It does NOT cover abstract questions about versions, principles, or mechanisms.
                  Examples:
                    "执行 mvn clean compile 命令" → CommandExecuteTool
                    "run git status to see what files changed" → CommandExecuteTool
                    "帮我查看当前 maven 的版本" → CommandExecuteTool (mvn is a system command)

                RULE 4 — Code understanding question (How / What / Why / Explain) → NONE
                  Match: 怎么 / 什么 / 为什么 / 如何 / 工作原理 / 调用链 / 自愈 / 设计 / 区别 / 原理 / 解释 / 是怎么 / 做什么
                  This is the SEMANTIC FALLBACK for code understanding. It does NOT participate in tool routing — it only catches questions where the user wants explanation, not file/system action.
                  When this rule matches, output NONE (RAG handles the explanation).
                  Excludes 哪里 — used in search context ("find where X is used") is not an understanding question.
                  Examples:
                    "PluginBasedStreamingAgentService 怎么实现自愈" → NONE
                    "PluginBasedStreamingAgentService 工作原理" → NONE
                    "PluginBasedStreamingAgentService 做什么" → NONE
                    "PluginBasedStreamingAgentService 自愈机制" → NONE
                    "PluginBasedStreamingAgentService 调用链" → NONE
                    "PluginBasedStreamingAgentService 为什么这样设计" → NONE
                    "MemoryService 怎么存长期记忆" → NONE
                    "什么是 Spring Boot" → NONE
                    "Java 和 Python 有什么区别" → NONE
                    "当前的项目主要是做什么的" → NONE
                    "这个项目主要做什么" → NONE

                RULE 5 — WRITE or CREATE a file → FileWriteTool
                  Match: 写 / 创建 / 生成 / 保存 / write / create / generate
                  Examples:
                    "帮我创建一个新的 Java 类 HelloWorld.java" → FileWriteTool
                    "write a README.md file" → FileWriteTool
                    "生成一个 application.properties 配置文件" → FileWriteTool

                RULE 6 — SEARCH or FIND files matching a text pattern (PURE text retrieval) → GrepTool
                  Match: 搜索 / 查找 / 找出 / 搜 / search / find / locate + a search pattern
                  This rule fires ONLY when there is NO understanding question word in the message. If the message contains 怎么 / 什么 / 为什么 / 如何 / 原理 / 机制 / 区别 / 解释, Rule 4 wins → NONE. The search verb alone does not override an understanding question.
                  Examples:
                    "搜索 src/main/java 里包含 ToolSelector 的文件" → GrepTool
                    "find all Java files containing ToolRegistry" → GrepTool
                    "在项目里搜一下哪里用了 @PostConstruct" → GrepTool (哪里 is excluded from Rule 4 — pure search context)

                RULE 7 — LIST project structure or directory contents → ProjectScanTool
                  Match: 列出 / 项目结构 / 目录结构 / 整体布局 / scan / list + project reference
                  This rule fires ONLY when the user explicitly asks for directory layout or file listing. If the message contains any understanding question word (怎么 / 什么 / 为什么 / 如何 / 做什么 / 原理 / 机制 / 区别 / 解释), Rule 4 wins → NONE.
                  A reference to "项目" (project) alone is NOT enough — the message must ask for the structure/layout, not explain what the project does.
                  Examples:
                    "帮我分析这个项目的目录结构" → ProjectScanTool
                    "scan the project structure and list all files" → ProjectScanTool
                    "列出项目里所有的文件和文件夹" → ProjectScanTool
                    "看看这个项目的整体布局" → ProjectScanTool

                RULE 8 — Otherwise → NONE

                Available tools:
                %s

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
