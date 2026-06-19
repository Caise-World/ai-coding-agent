package com.aicoding.agent.service;

import com.aicoding.agent.dto.ChatRequest;
import com.aicoding.agent.dto.ChatResponse;
import com.aicoding.agent.tool.FileReadTool;
import com.aicoding.agent.tool.ProjectScanTool;
import com.aicoding.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.*;

@Service
public class ReActAgentService {

    private static final int MAX_ITERATIONS = 5;
    private static final Pattern ACTION_PATTERN = Pattern.compile(
        "Action:\\s*(\\w+)\\s*\\(\\s*[\"']?([^\"'()]+)[\"']?\\s*\\)", Pattern.CASE_INSENSITIVE);

    private final LLMService llmService;
    private final ProjectScanTool projectScanTool;
    private final FileReadTool fileReadTool;
    private final String defaultProjectPath;

    public ReActAgentService(
            LLMService llmService,
            ProjectScanTool projectScanTool,
            FileReadTool fileReadTool,
            @Value("${agent.project-path}") String defaultProjectPath) {
        this.llmService = llmService;
        this.projectScanTool = projectScanTool;
        this.fileReadTool = fileReadTool;
        this.defaultProjectPath = defaultProjectPath;
    }

    public ChatResponse chat(ChatRequest request) {
        String userMessage = request.getMessage();
        String projectPath = request.getPath() != null ? request.getPath() : defaultProjectPath;

        Map<String, Tool> toolMap = new HashMap<>();
        toolMap.put("ProjectScanTool", projectScanTool);
        toolMap.put("FileReadTool", fileReadTool);

        List<String> conversationHistory = new ArrayList<>();
        String systemPrompt = buildReactSystemPrompt(projectPath);

        // Initial user message
        conversationHistory.add("User: " + userMessage);

        String finalAnswer = null;
        int iterations = 0;

        while (iterations < MAX_ITERATIONS) {
            iterations++;

            // Build full prompt with history
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

            // Try to parse tool call from text
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
                // No more tool calls, this is the final answer
                finalAnswer = extractFinalAnswer(content);
                break;
            } else {
                // Response doesn't seem to be a final answer or tool call
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
                You are a coding assistant using the ReAct pattern.

                Available tools:
                - ProjectScanTool: Scans a project directory. Input: absolute directory path
                - FileReadTool: Reads file content. Input: absolute file path

                ReAct format:
                Thought: ... (your reasoning)
                Action: ToolName(input)
                Observation: ... (result from previous action)

                When you have enough information, provide your final answer.

                Default project path: %s

                Important: Use the format Action: ToolName(input) when calling tools.
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
               (content.length() < 200 && !content.contains("Action:"));
    }

    private String extractFinalAnswer(String content) {
        // Try to extract the actual answer part
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
