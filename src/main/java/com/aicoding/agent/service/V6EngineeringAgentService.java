package com.aicoding.agent.service;

import com.aicoding.agent.dto.ChatRequest;
import com.aicoding.agent.dto.ChatResponse;
import com.aicoding.agent.memory.MemoryService;
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
    private final MemoryService memoryService;
    private final LLMService llmService;
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
            MemoryService memoryService,
            LLMService llmService,
            ProjectScanTool projectScanTool,
            FileReadTool fileReadTool,
            FileWriteTool fileWriteTool,
            CommandExecuteTool commandExecuteTool,
            @Value("${agent.project-path}") String defaultProjectPath) {
        this.taskPlannerService = taskPlannerService;
        this.failureAnalysisService = failureAnalysisService;
        this.traceService = traceService;
        this.memoryService = memoryService;
        this.llmService = llmService;
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

        // Phase 0: Memory Retrieval
        String memoryContext = memoryService.getMemoryContext();
        memoryService.saveShortTerm(sessionId, "userMessage", userMessage);
        memoryService.saveShortTerm(sessionId, "projectPath", projectPath);
        memoryService.saveShortTerm(sessionId, "startTime", System.currentTimeMillis());

        StringBuilder finalReport = new StringBuilder();
        finalReport.append("=== V6 Engineering-grade Agent ===\n");
        finalReport.append("Session: ").append(sessionId).append("\n\n");

        // Phase 1: Planning (with memory context)
        stepCounter++;
        traceService.addTrace(stepCounter, "MEMORY_RETRIEVAL", userMessage, null,
                memoryContext, "Retrieved relevant experiences from memory", true);

        finalReport.append("[Phase 0] Memory Retrieval\n");
        finalReport.append(memoryContext.isBlank() ? "No relevant prior experience.\n" : memoryContext);
        finalReport.append("\n[Phase 1] Planning\n");

        List<SubTask> subtasks = taskPlannerService.plan(userMessage, projectPath);
        finalReport.append("Planned ").append(subtasks.size()).append(" tasks\n");

        memoryService.saveShortTerm(sessionId, "plan", subtasks.toString());

        // Phase 2: Execute + Verify + Recover Loop
        finalReport.append("\n[Phase 2] Execute-Verify-Recover Loop\n");
        boolean allSuccess = false;
        int loopCount = 0;

        while (loopCount < MAX_LOOP_ITERATIONS && !allSuccess) {
            loopCount++;
            stepCounter++;
            finalReport.append("\n--- Loop ").append(loopCount).append(" ---\n");

            for (SubTask task : subtasks) {
                if (task.getState() == TaskState.SUCCESS) {
                    finalReport.append("[SKIP] Task ").append(task.getId()).append(" already completed\n");
                    continue;
                }

                stepCounter++;
                task.setState(TaskState.RUNNING);
                finalReport.append("[EXEC] Task ").append(task.getId()).append(": ").append(task.getDescription()).append("\n");

                // Save execution to short-term memory
                memoryService.saveShortTerm(sessionId, "currentTask", task.getDescription());
                memoryService.saveShortTerm(sessionId, "currentLoop", loopCount);

                // Execute
                String reasoning = "Executing " + task.getTool() + " with input: " + truncate(task.getInput(), 50);
                traceService.addTrace(stepCounter, "EXECUTION", task.getInput(), task.getTool(),
                        "", reasoning, true);

                executeSubTask(task);
                memoryService.saveShortTerm(sessionId, "lastResult", task.getResult());

                // Verify
                boolean verified = simpleVerify(task);
                if (verified) {
                    task.setState(TaskState.SUCCESS);
                    stepCounter++;
                    traceService.addTrace(stepCounter, "VERIFICATION", task.getDescription(), task.getTool(),
                            "Task completed successfully", "Output verified as successful", true);
                    finalReport.append("  [SUCCESS]\n");

                    // Save success experience to long-term memory
                    memoryService.saveExperience(task.getTool(), task.getResult(), task.getDescription());
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

                    // Save failure to long-term memory
                    memoryService.saveFailure(task.getDescription(), analysis.getFailureReason(), analysis.getFixStrategy());

                    // Phase 4: Recovery
                    if (loopCount < MAX_LOOP_ITERATIONS) {
                        stepCounter++;
                        finalReport.append("\n[Phase 4] Recovery Attempt\n");
                        traceService.addTrace(stepCounter, "RECOVERY", task.getDescription(), task.getTool(),
                                "Retrying with adjusted approach", "Applying fix strategy", true);
                        task.setState(TaskState.PENDING);
                        finalReport.append("  Retrying task...\n");
                    }
                }
            }

            allSuccess = subtasks.stream().allMatch(t -> t.getState() == TaskState.SUCCESS);
        }

        // Phase 5: Finalization
        stepCounter++;
        finalReport.append("\n[Phase 5] Finalization\n");
        String traceReport = traceService.generateTraceReport();

        traceService.addTrace(stepCounter, "FINALIZATION", "All tasks complete", null,
                traceReport, "Generating final summary", true);

        long successCount = subtasks.stream().filter(t -> t.getState() == TaskState.SUCCESS).count();
        finalReport.append("Completed: ").append(successCount).append("/").append(subtasks.size()).append(" tasks\n");
        finalReport.append("\n--- Trace Report ---\n").append(traceReport);

        // Save final state to short-term memory
        memoryService.saveShortTerm(sessionId, "endTime", System.currentTimeMillis());
        memoryService.saveShortTerm(sessionId, "successCount", successCount);
        memoryService.saveShortTerm(sessionId, "totalTasks", subtasks.size());

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
