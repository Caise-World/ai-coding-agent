package com.aicoding.agent.service;

import com.aicoding.agent.dto.ChatRequest;
import com.aicoding.agent.dto.ChatResponse;
import com.aicoding.agent.tool.FileReadTool;
import com.aicoding.agent.tool.ProjectScanTool;
import com.aicoding.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CodingAgentService {

    private final LLMService llmService;
    private final ProjectScanTool projectScanTool;
    private final FileReadTool fileReadTool;
    private final String defaultProjectPath;

    public CodingAgentService(
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

        String systemPrompt = buildSystemPrompt(projectPath);

        LLMService.LLMResponse llmResponse = llmService.chat(systemPrompt, userMessage);

        if (llmResponse.error() != null) {
            return new ChatResponse(llmResponse.error(), false, null);
        }

        if (llmResponse.toolCall() != null) {
            String toolName = llmResponse.toolCall().name();
            String toolInput = parseToolArguments(toolName, llmResponse.toolCall().arguments());

            Tool tool = toolMap.get(toolName);
            if (tool != null) {
                String toolResult = tool.execute(toolInput);
                String finalAnswer = generateFinalAnswer(userMessage, toolName, toolResult);
                return new ChatResponse(finalAnswer, true, toolName);
            }
        }

        String answer = llmResponse.content() != null ? llmResponse.content() : "No response";
        return new ChatResponse(answer, false, null);
    }

    private String buildSystemPrompt(String projectPath) {
        return """
                You are a helpful coding assistant that helps analyze projects and read files.

                Available tools:
                1. ProjectScanTool - Scans a project directory and returns its structure
                   Use when: User asks to analyze project structure, list files, or understand the project layout
                   Input: absolute directory path (e.g., /Users/wanshun/Documents/Code/myproject)

                2. FileReadTool - Reads the content of a file
                   Use when: User asks to read, view, or analyze specific files
                   Input: absolute file path (e.g., /Users/wanshun/Documents/Code/myproject/pom.xml)

                Instructions:
                - Always use tools when the user asks about project structure or file content
                - If the user provides a path, use that path
                - If no path is provided, use the default path: %s
                - Return ONLY a JSON object for tool call like:
                  {"toolName":"ToolName","arguments":"input"}
                - Do NOT include any other text in your response when you decide to use a tool
                - If the user asks a general question not requiring tools, answer directly

                Important: Only make ONE tool call per response. Do NOT loop.
                """.formatted(projectPath);
    }

    private String parseToolArguments(String toolName, String arguments) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(arguments);
            if (node.has("input")) {
                return node.get("input").asText();
            }
            return node.asText();
        } catch (Exception e) {
            return arguments;
        }
    }

    private String generateFinalAnswer(String userMessage, String toolName, String toolResult) {
        String prompt = """
                Based on the following tool result, provide a concise answer to the user's question.

                User question: %s

                Tool used: %s
                Tool result:
                %s

                Please provide a clear, helpful answer summarizing the findings.
                """.formatted(userMessage, toolName, toolResult);

        LLMService.LLMResponse answerResponse = llmService.chat(
                "You are a helpful assistant.", prompt);

        return answerResponse.content() != null ? answerResponse.content() : "Unable to generate answer";
    }
}
