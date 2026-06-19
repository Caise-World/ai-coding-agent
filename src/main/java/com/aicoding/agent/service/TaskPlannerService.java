package com.aicoding.agent.service;

import com.aicoding.agent.model.SubTask;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TaskPlannerService {

    private final LLMService llmService;
    private final ObjectMapper objectMapper;
    private final Pattern SUBTASK_PATTERN = Pattern.compile(
        "\\{\\s*\"id\"\\s*:\\s*(\\d+)\\s*,\\s*\"description\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"tool\"\\s*:\\s*\"(\\w+)\"\\s*,\\s*\"input\"\\s*:\\s*\"([^\"]+)\"\\s*\\}",
        Pattern.CASE_INSENSITIVE);

    public TaskPlannerService(LLMService llmService) {
        this.llmService = llmService;
        this.objectMapper = new ObjectMapper();
    }

    public List<SubTask> plan(String userMessage, String projectPath) {
        String prompt = buildPlanningPrompt(userMessage, projectPath);
        LLMService.LLMResponse response = llmService.chat(prompt);

        if (response.error() != null) {
            return List.of(new SubTask(1, userMessage, "ProjectScanTool", projectPath));
        }

        return parseSubTasks(response.content());
    }

    private String buildPlanningPrompt(String userMessage, String projectPath) {
        return """
                You are a task planner. Break down the user's request into subtasks.

                Available tools:
                - ProjectScanTool: Scan project structure. Input: absolute directory path
                - FileReadTool: Read file content. Input: absolute file path
                - FileWriteTool: Write content to file. Input: "filePath|content"
                - CommandExecuteTool: Execute shell commands. Input: command

                User request: %s
                Project path: %s

                Respond ONLY with a JSON array of subtasks in this format:
                [
                  {"id": 1, "description": "...", "tool": "ToolName", "input": "..."},
                  {"id": 2, "description": "...", "tool": "ToolName", "input": "..."}
                ]

                Rules:
                - Use the appropriate tool for each subtask
                - Each subtask should be atomic (can be executed independently)
                - Output ONLY the JSON array, no other text
                - Maximum 5 subtasks
                """.formatted(userMessage, projectPath);
    }

    private List<SubTask> parseSubTasks(String content) {
        List<SubTask> subtasks = new ArrayList<>();

        if (content == null || content.isBlank()) {
            return subtasks;
        }

        // Try JSON array format first
        try {
            JsonNode root = objectMapper.readTree(content);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    SubTask task = new SubTask();
                    task.setId(node.get("id").asInt());
                    task.setDescription(node.get("description").asText());
                    task.setTool(node.get("tool").asText());
                    task.setInput(node.get("input").asText());
                    subtasks.add(task);
                }
                return subtasks;
            }
        } catch (JsonProcessingException e) {
            // Fall through to regex parsing
        }

        // Fall back to regex parsing
        Matcher matcher = SUBTASK_PATTERN.matcher(content);
        int id = 1;
        while (matcher.find() && id <= 5) {
            SubTask task = new SubTask(
                id++,
                matcher.group(2),
                matcher.group(3),
                matcher.group(4)
            );
            subtasks.add(task);
        }

        if (subtasks.isEmpty()) {
            // Default: return single task
            subtasks.add(new SubTask(1, "Default task", "ProjectScanTool", "/Users/wanshun/Documents/Code"));
        }

        return subtasks;
    }
}
