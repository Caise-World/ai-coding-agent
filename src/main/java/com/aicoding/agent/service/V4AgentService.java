package com.aicoding.agent.service;

import com.aicoding.agent.dto.ChatRequest;
import com.aicoding.agent.dto.ChatResponse;
import com.aicoding.agent.model.SubTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class V4AgentService {

    private final TaskPlannerService taskPlannerService;
    private final TaskExecutorService taskExecutorService;
    private final LLMService llmService;
    private final String defaultProjectPath;

    public V4AgentService(
            TaskPlannerService taskPlannerService,
            TaskExecutorService taskExecutorService,
            LLMService llmService,
            @Value("${agent.project-path}") String defaultProjectPath) {
        this.taskPlannerService = taskPlannerService;
        this.taskExecutorService = taskExecutorService;
        this.llmService = llmService;
        this.defaultProjectPath = defaultProjectPath;
    }

    public ChatResponse chat(ChatRequest request) {
        String userMessage = request.getMessage();
        String projectPath = request.getPath() != null ? request.getPath() : defaultProjectPath;

        // Step 1: Task Planning - break down into subtasks
        List<SubTask> subtasks = taskPlannerService.plan(userMessage, projectPath);

        // Step 2: Execute all subtasks
        String executionResult = taskExecutorService.executeAll(subtasks);

        // Step 3: Generate final answer using LLM
        String finalAnswer = generateFinalAnswer(userMessage, subtasks, executionResult);

        return new ChatResponse(finalAnswer, subtasks.size() > 1, null);
    }

    private String generateFinalAnswer(String userMessage, List<SubTask> subtasks, String executionResult) {
        String subtaskSummary = buildSubtaskSummary(subtasks);

        String prompt = """
                Based on the following task execution results, provide a clear answer to the user.

                User's original request: %s

                Tasks planned and executed:
                %s

                Execution results:
                %s

                Please provide a concise summary of what was done and the results.
                """.formatted(userMessage, subtaskSummary, executionResult);

        LLMService.LLMResponse response = llmService.chat(prompt);

        return response.content() != null ? response.content() : executionResult;
    }

    private String buildSubtaskSummary(List<SubTask> subtasks) {
        StringBuilder sb = new StringBuilder();
        for (SubTask task : subtasks) {
            sb.append("- [").append(task.isCompleted() ? "✓" : "✗").append("] ");
            sb.append(task.getDescription());
            sb.append(" (").append(task.getTool()).append(")\n");
        }
        return sb.toString();
    }
}
