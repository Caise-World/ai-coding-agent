package com.aicoding.agent.service;

import com.aicoding.agent.dto.ChatRequest;
import com.aicoding.agent.dto.ChatResponse;
import com.aicoding.agent.tool.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.*;

@Service
public class ReActAgentService {

    private static final int MAX_ITERATIONS = 8;
    private static final Pattern ACTION_PATTERN = Pattern.compile(
        "Action:\\s*(\\w+)\\s*\\(\\s*([\\s\\S]*?)\\s*\\)", Pattern.CASE_INSENSITIVE);

    private final LLMService llmService;
    private final ProjectScanTool projectScanTool;
    private final FileReadTool fileReadTool;
    private final FileWriteTool fileWriteTool;
    private final CommandExecuteTool commandExecuteTool;
    private final String defaultProjectPath;

    public ReActAgentService(
            LLMService llmService,
            ProjectScanTool projectScanTool,
            FileReadTool fileReadTool,
            FileWriteTool fileWriteTool,
            CommandExecuteTool commandExecuteTool,
            @Value("${agent.project-path}") String defaultProjectPath) {
        this.llmService = llmService;
        this.projectScanTool = projectScanTool;
        this.fileReadTool = fileReadTool;
        this.fileWriteTool = fileWriteTool;
        this.commandExecuteTool = commandExecuteTool;
        this.defaultProjectPath = defaultProjectPath;
    }

    public ChatResponse chat(ChatRequest request) {
        String userMessage = request.getMessage();
        String projectPath = request.getPath() != null ? request.getPath() : defaultProjectPath;

        Map<String, Tool> toolMap = new HashMap<>();
        toolMap.put("ProjectScanTool", projectScanTool);
        toolMap.put("FileReadTool", fileReadTool);
        toolMap.put("FileWriteTool", fileWriteTool);
        toolMap.put("CommandExecuteTool", commandExecuteTool);

        List<String> conversationHistory = new ArrayList<>();
        String systemPrompt = buildReactSystemPrompt(projectPath);

        conversationHistory.add("User: " + userMessage);

        String finalAnswer = null;
        int iterations = 0;

        while (iterations < MAX_ITERATIONS) {
            iterations++;

            String fullPrompt = buildFullPrompt(systemPrompt, conversationHistory);
            LLMService.LLMResponse response = llmService.chat(fullPrompt);

            if (response.error() != null) {
                return new ChatResponse(response.error(), false, null);
            }

            String content = response.content();
            if (content == null || content.isBlank()) {
                finalAnswer = "No response from LLM";
                break;
            }

            conversationHistory.add("Assistant: " + content);

            ToolCallInfo toolCallInfo = parseToolCall(content);

            if (toolCallInfo != null) {
                Tool tool = toolMap.get(toolCallInfo.toolName);
                if (tool != null) {
                    String toolResult = tool.execute(toolCallInfo.input);
                    conversationHistory.add("Observation: " + toolResult);
                } else {
                    conversationHistory.add("Observation: Tool not found: " + toolCallInfo.toolName);
                }
            } else if (isFinalAnswer(content)) {
                finalAnswer = extractFinalAnswer(content);
                break;
            } else {
                finalAnswer = content;
                break;
            }
        }

        if (finalAnswer == null) {
            finalAnswer = "Max iterations (" + MAX_ITERATIONS + ") reached without final answer";
        }

        return new ChatResponse(finalAnswer, iterations > 1, null);
    }

    private String buildReactSystemPrompt(String projectPath) {
        return """
                You are an expert coding assistant using the ReAct pattern.

                Available tools:
                1. ProjectScanTool: Scans project structure. Input: absolute directory path
                2. FileReadTool: Reads file content. Input: absolute file path
                3. FileWriteTool: Writes content to file. Input: "filePath|content" (separated by pipe)
                4. CommandExecuteTool: Executes shell commands. Input: absolute command

                Supported commands:
                - mvn test: Run tests
                - mvn compile: Compile project
                - cd /path && mvn test: Run tests in specific directory

                ReAct format:
                Thought: ... (your reasoning)
                Action: ToolName(input)
                Observation: ... (result)

                When you have enough information, provide your final answer.

                Default project path: %s

                IMPORTANT:
                - Use FileWriteTool when user asks to modify, create, or update files
                - Use CommandExecuteTool when user asks to run tests, compile, or execute commands
                - Always provide the absolute path when using tools
                """.formatted(projectPath);
    }

    private String buildFullPrompt(String systemPrompt, List<String> history) {
        StringBuilder sb = new StringBuilder();
        sb.append(systemPrompt).append("\n\n");
        sb.append("Conversation history:\n");
        for (String turn : history) {
            sb.append(turn).append("\n");
        }
        sb.append("\nContinue your response:");
        return sb.toString();
    }

    private ToolCallInfo parseToolCall(String content) {
        Matcher matcher = ACTION_PATTERN.matcher(content);
        if (matcher.find()) {
            String toolName = matcher.group(1);
            String input = matcher.group(2).trim();
            return new ToolCallInfo(toolName, input);
        }
        return null;
    }

    private boolean isFinalAnswer(String content) {
        String lower = content.toLowerCase();
        return lower.contains("final answer") ||
               lower.contains("answer:") ||
               lower.contains("最终答案") ||
               (content.length() < 300 && !content.contains("Action:"));
    }

    private String extractFinalAnswer(String content) {
        int idx = content.toLowerCase().indexOf("final answer");
        if (idx >= 0) {
            return content.substring(idx + "final answer".length()).trim();
        }
        idx = content.toLowerCase().indexOf("answer:");
        if (idx >= 0) {
            return content.substring(idx + "answer:".length()).trim();
        }
        return content.trim();
    }

    private record ToolCallInfo(String toolName, String input) {}
}
