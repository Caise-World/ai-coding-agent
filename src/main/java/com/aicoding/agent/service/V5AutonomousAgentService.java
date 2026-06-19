package com.aicoding.agent.service;

import com.aicoding.agent.dto.ChatRequest;
import com.aicoding.agent.dto.ChatResponse;
import com.aicoding.agent.model.SubTask;
import com.aicoding.agent.model.TaskState;
import com.aicoding.agent.tool.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class V5AutonomousAgentService {

    private static final int MAX_LOOP_ITERATIONS = 3;
    private static final int MAX_RETRIES = 1;

    private final TaskPlannerService taskPlannerService;
    private final TaskVerifierService taskVerifierService;
    private final LLMService llmService;
    private final ProjectScanTool projectScanTool;
    private final FileReadTool fileReadTool;
    private final FileWriteTool fileWriteTool;
    private final CommandExecuteTool commandExecuteTool;
    private final String defaultProjectPath;

    public V5AutonomousAgentService(
            TaskPlannerService taskPlannerService,
            TaskVerifierService taskVerifierService,
            LLMService llmService,
            ProjectScanTool projectScanTool,
            FileReadTool fileReadTool,
            FileWriteTool fileWriteTool,
            CommandExecuteTool commandExecuteTool,
            @Value("${agent.project-path}") String defaultProjectPath) {
        this.taskPlannerService = taskPlannerService;
        this.taskVerifierService = taskVerifierService;
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

        StringBuilder executionLog = new StringBuilder();
        executionLog.append("=== V5 Autonomous Agent Started ===\n\n");

        // Step 1: Plan tasks
        executionLog.append("[1] Planning tasks...\n");
        List<SubTask> subtasks = taskPlannerService.plan(userMessage, projectPath);
        executionLog.append("Planned ").append(subtasks.size()).append(" subtasks\n\n");

        // Step 2: Execute + Verify Loop
        executionLog.append("[2] Executing tasks...\n");
        int loopCount = 0;
        boolean allSuccess = false;

        while (loopCount < MAX_LOOP_ITERATIONS && !allSuccess) {
            loopCount++;
            executionLog.append("\n--- Loop ").append(loopCount).append(" ---\n");

            for (SubTask task : subtasks) {
                if (task.getState() == TaskState.SUCCESS) {
                    executionLog.append("[SKIP] Task ").append(task.getId()).append(" already completed\n");
                    continue;
                }

                executionLog.append("[EXEC] Task ").append(task.getId()).append(": ").append(task.getDescription()).append("\n");
                task.setState(TaskState.RUNNING);

                executeSubTask(task);

                if (task.getResult() != null && task.getResult().length() > 150) {
                    executionLog.append("  Result: ").append(task.getResult().substring(0, 150)).append("...\n");
                } else {
                    executionLog.append("  Result: ").append(task.getResult()).append("\n");
                }

                // Quick verification (no extra LLM call)
                if (simpleVerify(task)) {
                    task.setState(TaskState.SUCCESS);
                    executionLog.append("  [SUCCESS]\n");
                } else {
                    task.setState(TaskState.FAILED);
                    executionLog.append("  [FAILED]\n");
                }
            }

            allSuccess = subtasks.stream().allMatch(t -> t.getState() == TaskState.SUCCESS);

            if (!allSuccess && loopCount < MAX_LOOP_ITERATIONS) {
                executionLog.append("\nSome tasks failed, retrying...\n");
            }
        }

        // Step 3: Generate summary
        executionLog.append("\n[3] Generating summary...\n");
        String finalSummary = generateSummary(userMessage, subtasks, executionLog.toString());

        return new ChatResponse(finalSummary, subtasks.size() > 1, "V5");
    }

    private void executeSubTask(SubTask task) {
        Tool tool = getTool(task.getTool());
        if (tool == null) {
            task.setResult("Error: Unknown tool: " + task.getTool());
            return;
        }

        try {
            String result = tool.execute(task.getInput());
            task.setResult(result);
        } catch (Exception e) {
            task.setResult("Exception: " + e.getMessage());
        }
    }

    private boolean simpleVerify(SubTask task) {
        String result = task.getResult();
        if (result == null || result.isBlank()) return false;

        String lower = result.toLowerCase();
        return !lower.contains("error") &&
               !lower.contains("exception") &&
               !lower.contains("failed") &&
               !lower.contains("not found");
    }

    private String generateSummary(String userMessage, List<SubTask> subtasks, String log) {
        StringBuilder summary = new StringBuilder();
        summary.append("=== Task Completion Summary ===\n\n");
        summary.append("User request: ").append(userMessage).append("\n\n");

        summary.append("Tasks:\n");
        for (SubTask task : subtasks) {
            summary.append("- [").append(task.getState() == TaskState.SUCCESS ? "✓" : "✗").append("] ");
            summary.append(task.getDescription());
            summary.append("\n  Tool: ").append(task.getTool()).append("\n");
            summary.append("  State: ").append(task.getState()).append("\n");
        }

        long successCount = subtasks.stream().filter(t -> t.getState() == TaskState.SUCCESS).count();
        summary.append("\nCompleted: ").append(successCount).append("/").append(subtasks.size()).append("\n");

        return summary.toString();
    }

    private Tool getTool(String toolName) {
        if (toolName == null) return null;
        return switch (toolName) {
            case "ProjectScanTool" -> projectScanTool;
            case "FileReadTool" -> fileReadTool;
            case "FileWriteTool" -> fileWriteTool;
            case "CommandExecuteTool" -> commandExecuteTool;
            default -> null;
        };
    }
}
