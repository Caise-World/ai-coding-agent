package com.aicoding.agent.service;

import com.aicoding.agent.dto.AgentEvent;
import com.aicoding.agent.dto.ChatRequest;
import com.aicoding.agent.memory.MemoryService;
import com.aicoding.agent.model.*;
import com.aicoding.agent.tool.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class StreamingAgentService {

    private final TaskPlannerService taskPlannerService;
    private final FailureAnalysisService failureAnalysisService;
    private final MemoryService memoryService;
    private final LLMService llmService;
    private final ProjectScanTool projectScanTool;
    private final FileReadTool fileReadTool;
    private final FileWriteTool fileWriteTool;
    private final CommandExecuteTool commandExecuteTool;
    private final String defaultProjectPath;

    public StreamingAgentService(
            TaskPlannerService taskPlannerService,
            FailureAnalysisService failureAnalysisService,
            MemoryService memoryService,
            LLMService llmService,
            ProjectScanTool projectScanTool,
            FileReadTool fileReadTool,
            FileWriteTool fileWriteTool,
            CommandExecuteTool commandExecuteTool,
            String defaultProjectPath) {
        this.taskPlannerService = taskPlannerService;
        this.failureAnalysisService = failureAnalysisService;
        this.memoryService = memoryService;
        this.llmService = llmService;
        this.projectScanTool = projectScanTool;
        this.fileReadTool = fileReadTool;
        this.fileWriteTool = fileWriteTool;
        this.commandExecuteTool = commandExecuteTool;
        this.defaultProjectPath = defaultProjectPath;
    }

    public Flux<AgentEvent> executeStream(ChatRequest request) {
        Sinks.Many<AgentEvent> sink = Sinks.many().multicast().onBackpressureBuffer();

        // Run the streaming logic in a separate thread to avoid blocking
        CompletableFuture.runAsync(() -> {
            try {
                executeStreamInternal(request, sink);
            } catch (Exception e) {
                sink.tryEmitNext(AgentEvent.error("Streaming error: " + e.getMessage()));
            } finally {
                sink.tryEmitComplete();
            }
        });

        return sink.asFlux();
    }

    private void executeStreamInternal(ChatRequest request, Sinks.Many<AgentEvent> sink) {
        String sessionId = UUID.randomUUID().toString();
        String userMessage = request.getMessage();
        String projectPath = request.getPath() != null ? request.getPath() : defaultProjectPath;

        // Phase 0: Memory Retrieval
        sink.tryEmitNext(AgentEvent.thinking("Retrieving relevant experiences from memory..."));
        String memoryContext = memoryService.getMemoryContext();
        sink.tryEmitNext(AgentEvent.memoryRead(memoryContext.isBlank() ? "No prior experience found" : memoryContext));

        memoryService.saveShortTerm(sessionId, "userMessage", userMessage);
        memoryService.saveShortTerm(sessionId, "projectPath", projectPath);
        sink.tryEmitNext(AgentEvent.memoryWrite("Saved user request to short-term memory"));

        // Phase 1: Planning
        sink.tryEmitNext(AgentEvent.planning("Analyzing request and creating execution plan..."));
        List<SubTask> subtasks = taskPlannerService.plan(userMessage, projectPath);
        sink.tryEmitNext(AgentEvent.thinking("Created " + subtasks.size() + " subtasks"));

        memoryService.saveShortTerm(sessionId, "plan", subtasks.toString());

        // Phase 2: Execute Loop
        AtomicInteger loopCount = new AtomicInteger(0);
        boolean allSuccess = false;

        while (!allSuccess && loopCount.get() < 3) {
            int currentLoop = loopCount.incrementAndGet();
            sink.tryEmitNext(AgentEvent.thinking("Starting loop " + currentLoop));

            for (SubTask task : subtasks) {
                if (task.getState() == TaskState.SUCCESS) {
                    sink.tryEmitNext(AgentEvent.thinking("Task " + task.getId() + " already completed, skipping"));
                    continue;
                }

                task.setState(TaskState.RUNNING);
                sink.tryEmitNext(AgentEvent.thinking("Executing: " + task.getDescription()));

                // Tool Call
                sink.tryEmitNext(AgentEvent.toolCall(task.getTool(), task.getInput()));

                // Execute
                executeSubTask(task);

                // Tool Result
                sink.tryEmitNext(AgentEvent.toolResult(task.getTool(), truncateResult(task.getResult(), 300)));

                // Memory update
                memoryService.saveShortTerm(sessionId, "lastResult", task.getResult());

                // Verification
                boolean verified = simpleVerify(task);
                if (verified) {
                    task.setState(TaskState.SUCCESS);
                    sink.tryEmitNext(AgentEvent.verification("SUCCESS", "Task completed successfully"));
                    memoryService.saveExperience(task.getTool(), task.getResult(), task.getDescription());
                } else {
                    task.setState(TaskState.FAILED);
                    sink.tryEmitNext(AgentEvent.verification("FAILED", "Task did not complete as expected"));

                    // Failure Analysis
                    sink.tryEmitNext(AgentEvent.thinking("Analyzing failure..."));
                    FailureAnalysis analysis = failureAnalysisService.analyze(task);
                    sink.tryEmitNext(AgentEvent.failureAnalysis(
                            "Reason: " + analysis.getFailureReason() +
                            " | Fix: " + analysis.getFixStrategy()));

                    memoryService.saveFailure(task.getDescription(), analysis.getFailureReason(), analysis.getFixStrategy());

                    // Recovery
                    if (currentLoop < 3) {
                        sink.tryEmitNext(AgentEvent.recovery("Retrying task..."));
                        task.setState(TaskState.PENDING);
                    }
                }
            }

            allSuccess = subtasks.stream().allMatch(t -> t.getState() == TaskState.SUCCESS);
        }

        // Phase 3: Finalization
        long successCount = subtasks.stream().filter(t -> t.getState() == TaskState.SUCCESS).count();
        String summary = buildSummary(userMessage, subtasks, successCount);

        sink.tryEmitNext(AgentEvent.finalAnswer(summary));

        // Final memory update
        memoryService.saveShortTerm(sessionId, "endTime", System.currentTimeMillis());
        memoryService.saveShortTerm(sessionId, "successCount", successCount);
        sink.tryEmitNext(AgentEvent.memoryWrite("Session completed. Success: " + successCount + "/" + subtasks.size()));
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

    private String buildSummary(String userMessage, List<SubTask> subtasks, long successCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Task Completion Summary ===\n\n");
        sb.append("User request: ").append(userMessage).append("\n\n");
        sb.append("Tasks:\n");
        for (SubTask task : subtasks) {
            sb.append("- [").append(task.getState() == TaskState.SUCCESS ? "✓" : "✗").append("] ");
            sb.append(task.getDescription()).append("\n");
            sb.append("  Tool: ").append(task.getTool()).append("\n");
        }
        sb.append("\nCompleted: ").append(successCount).append("/").append(subtasks.size()).append("\n");
        return sb.toString();
    }

    private String truncateResult(String result, int maxLen) {
        if (result == null) return "null";
        if (result.length() <= maxLen) return result;
        return result.substring(0, maxLen) + "...";
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
