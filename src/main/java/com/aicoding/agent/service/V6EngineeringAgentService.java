package com.aicoding.agent.service;

import com.aicoding.agent.dto.ChatRequest;
import com.aicoding.agent.dto.ChatResponse;
import com.aicoding.agent.model.*;
import com.aicoding.agent.tool.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class V6EngineeringAgentService {

    private static final int MAX_LOOP_ITERATIONS = 3;

    private final TaskPlannerService taskPlannerService;
    private final FailureAnalysisService failureAnalysisService;
    private final TraceService traceService;
    private final LLMService llmService;
    private final AgentMemory agentMemory;
    private final ProjectScanTool projectScanTool;
    private final FileReadTool fileReadTool;
    private final FileWriteTool fileWriteTool;
    private final CommandExecuteTool commandExecuteTool;
    private final String defaultProjectPath;

    private int stepCounter = 0;

    public V6EngineeringAgentService(
            TaskPlannerService taskPlannerService,
            FailureAnalysisService failureAnalysisService,
            TraceService traceService,
            LLMService llmService,
            ProjectScanTool projectScanTool,
            FileReadTool fileReadTool,
            FileWriteTool fileWriteTool,
            CommandExecuteTool commandExecuteTool,
            @Value("${agent.project-path}") String defaultProjectPath) {
        this.taskPlannerService = taskPlannerService;
        this.failureAnalysisService = failureAnalysisService;
        this.traceService = traceService;
        this.llmService = llmService;
        this.agentMemory = new AgentMemory();
        this.projectScanTool = projectScanTool;
        this.fileReadTool = fileReadTool;
        this.fileWriteTool = fileWriteTool;
        this.commandExecuteTool = commandExecuteTool;
        this.defaultProjectPath = defaultProjectPath;
    }

    public ChatResponse chat(ChatRequest request) {
        String sessionId = UUID.randomUUID().toString();
        traceService.startSession(sessionId);
        stepCounter = 0;

        String userMessage = request.getMessage();
        String projectPath = request.getPath() != null ? request.getPath() : defaultProjectPath;

        // Update short-term memory
        agentMemory.updateTaskState("userMessage", userMessage);
        agentMemory.updateTaskState("projectPath", projectPath);
        agentMemory.updateTaskState("sessionId", sessionId);

        StringBuilder finalReport = new StringBuilder();
        finalReport.append("=== V6 Engineering-grade Agent ===\n");
        finalReport.append("Session: ").append(sessionId).append("\n\n");

        // Phase 1: Planning
        stepCounter++;
        traceService.addTrace(stepCounter, "PLANNING", userMessage, null,
                "Starting task planning", "Analyze user request and create execution plan", true);

        finalReport.append("[Phase 1] Planning\n");
        List<SubTask> subtasks = taskPlannerService.plan(userMessage, projectPath);
        finalReport.append("Planned ").append(subtasks.size()).append(" tasks\n");

        // Phase 2: Execute + Verify + Recover Loop
        finalReport.append("\n[Phase 2] Execute-Verify-Recover Loop\n");
        boolean allSuccess = false;
        int loopCount = 0;

        while (loopCount < MAX_LOOP_ITERATIONS && !allSuccess) {
            loopCount++;
            stepCounter++;
            finalReport.append("\n--- Loop ").append(loopCount).append(" ---\n");

            agentMemory.updateTaskState("currentLoop", loopCount);

            for (SubTask task : subtasks) {
                if (task.getState() == TaskState.SUCCESS) {
                    finalReport.append("[SKIP] Task ").append(task.getId()).append(" already completed\n");
                    continue;
                }

                stepCounter++;
                task.setState(TaskState.RUNNING);
                finalReport.append("[EXEC] Task ").append(task.getId()).append(": ").append(task.getDescription()).append("\n");

                // Execute
                String reasoning = "Executing " + task.getTool() + " with input: " + truncate(task.getInput(), 50);
                traceService.addTrace(stepCounter, "EXECUTION", task.getInput(), task.getTool(),
                        "", reasoning, true);

                executeSubTask(task);

                // Verify
                boolean verified = simpleVerify(task);
                if (verified) {
                    task.setState(TaskState.SUCCESS);
                    stepCounter++;
                    traceService.addTrace(stepCounter, "VERIFICATION", task.getDescription(), task.getTool(),
                            "Task completed successfully", "Output verified as successful", true);
                    finalReport.append("  [SUCCESS]\n");
                } else {
                    task.setState(TaskState.FAILED);
                    finalReport.append("  [FAILED]\n");

                    // Phase 3: Failure Analysis
                    stepCounter++;
                    finalReport.append("\n[Phase 3] Failure Analysis\n");
                    FailureAnalysis analysis = failureAnalysisService.analyze(task);

                    traceService.addTrace(stepCounter, "FAILURE_ANALYSIS", task.getDescription(), null,
                            "Reason: " + analysis.getFailureReason() +
                            ", Root cause: " + analysis.getRootCauseHypothesis() +
                            ", Fix: " + analysis.getFixStrategy(),
                            "Analyzing why task failed", true);

                    finalReport.append("  Failure Reason: ").append(analysis.getFailureReason()).append("\n");
                    finalReport.append("  Root Cause: ").append(analysis.getRootCauseHypothesis()).append("\n");
                    finalReport.append("  Fix Strategy: ").append(analysis.getFixStrategy()).append("\n");

                    // Store in long-term memory
                    agentMemory.addSolvedPattern(
                            analysis.getFailureReason(),
                            analysis.getFixStrategy(),
                            task.getDescription()
                    );

                    // Phase 4: Recovery (retry with fix)
                    if (loopCount < MAX_LOOP_ITERATIONS) {
                        stepCounter++;
                        finalReport.append("\n[Phase 4] Recovery Attempt\n");
                        traceService.addTrace(stepCounter, "RECOVERY", task.getDescription(), task.getTool(),
                                "Retrying with adjusted approach", "Applying fix strategy: " + analysis.getFixStrategy(), true);

                        // Simple retry - in real system would apply fix strategy
                        task.setState(TaskState.PENDING);
                        finalReport.append("  Retrying task...\n");
                    }
                }
            }

            allSuccess = subtasks.stream().allMatch(t -> t.getState() == TaskState.SUCCESS);
        }

        // Phase 5: Finalize
        stepCounter++;
        finalReport.append("\n[Phase 5] Finalization\n");
        String traceReport = traceService.generateTraceReport();

        traceService.addTrace(stepCounter, "FINALIZATION", "All tasks complete", null,
                traceReport, "Generating final summary", true);

        long successCount = subtasks.stream().filter(t -> t.getState() == TaskState.SUCCESS).count();
        finalReport.append("Completed: ").append(successCount).append("/").append(subtasks.size()).append(" tasks\n");
        finalReport.append("\n--- Trace Report ---\n").append(traceReport);

        // Clear short-term memory for next session
        agentMemory.clearTaskState();

        return new ChatResponse(finalReport.toString(), subtasks.size() > 1, "V6");
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

    private String truncate(String str, int maxLen) {
        if (str == null) return "null";
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen) + "...";
    }
}
