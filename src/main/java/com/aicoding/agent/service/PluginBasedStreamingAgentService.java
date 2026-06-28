package com.aicoding.agent.service;

import com.aicoding.agent.agent.AgenticLoopRunner;
import com.aicoding.agent.dto.AgentEvent;
import com.aicoding.agent.dto.ChatRequest;
import com.aicoding.agent.memory.MemoryService;
import com.aicoding.agent.model.*;
import com.aicoding.agent.rag.RagService;
import com.aicoding.agent.rag.routing.CodeQuestionDetector;
import com.aicoding.agent.rag.routing.DeterministicRouter;
import com.aicoding.agent.rag.workspace.WorkspaceService;
import com.aicoding.agent.registry.ToolExecutor;
import com.aicoding.agent.registry.ToolRegistry;
import com.aicoding.agent.registry.ToolSelector;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class PluginBasedStreamingAgentService {

    private static final int MAX_RETRIES = 3;

    private final ToolRegistry toolRegistry;
    private final ToolSelector toolSelector;
    private final ToolExecutor toolExecutor;
    private final MemoryService memoryService;
    private final LLMService llmService;
    private final RagService ragService;
    private final CodeQuestionDetector codeQuestionDetector;
    private final DeterministicRouter deterministicRouter;
    private final WorkspaceService workspaceService;
    private final AgenticLoopRunner agenticLoopRunner;
    private final String defaultProjectPath;

    public PluginBasedStreamingAgentService(
            ToolRegistry toolRegistry,
            ToolSelector toolSelector,
            ToolExecutor toolExecutor,
            MemoryService memoryService,
            LLMService llmService,
            RagService ragService,
            CodeQuestionDetector codeQuestionDetector,
            DeterministicRouter deterministicRouter,
            WorkspaceService workspaceService,
            AgenticLoopRunner agenticLoopRunner,
            String defaultProjectPath) {
        this.toolRegistry = toolRegistry;
        this.toolSelector = toolSelector;
        this.toolExecutor = toolExecutor;
        this.memoryService = memoryService;
        this.llmService = llmService;
        this.ragService = ragService;
        this.codeQuestionDetector = codeQuestionDetector;
        this.deterministicRouter = deterministicRouter;
        this.workspaceService = workspaceService;
        this.agenticLoopRunner = agenticLoopRunner;
        this.defaultProjectPath = defaultProjectPath;
    }

    public PluginBasedStreamingAgentService(
            ToolRegistry toolRegistry,
            ToolSelector toolSelector,
            ToolExecutor toolExecutor,
            MemoryService memoryService,
            LLMService llmService,
            RagService ragService,
            CodeQuestionDetector codeQuestionDetector,
            DeterministicRouter deterministicRouter,
            AgenticLoopRunner agenticLoopRunner,
            String defaultProjectPath) {
        this(toolRegistry, toolSelector, toolExecutor, memoryService, llmService, ragService,
                codeQuestionDetector, deterministicRouter, null, agenticLoopRunner, defaultProjectPath);
    }

    public PluginBasedStreamingAgentService(
            ToolRegistry toolRegistry,
            ToolSelector toolSelector,
            ToolExecutor toolExecutor,
            MemoryService memoryService,
            LLMService llmService,
            RagService ragService,
            CodeQuestionDetector codeQuestionDetector,
            AgenticLoopRunner agenticLoopRunner,
            String defaultProjectPath) {
        this(toolRegistry, toolSelector, toolExecutor, memoryService, llmService, ragService,
                codeQuestionDetector, null, agenticLoopRunner, defaultProjectPath);
    }

    public PluginBasedStreamingAgentService(
            ToolRegistry toolRegistry,
            ToolSelector toolSelector,
            ToolExecutor toolExecutor,
            MemoryService memoryService,
            LLMService llmService,
            RagService ragService,
            AgenticLoopRunner agenticLoopRunner,
            String defaultProjectPath) {
        this(toolRegistry, toolSelector, toolExecutor, memoryService, llmService, ragService,
                null, null, agenticLoopRunner, defaultProjectPath);
    }

    public PluginBasedStreamingAgentService(
            ToolRegistry toolRegistry,
            ToolSelector toolSelector,
            ToolExecutor toolExecutor,
            MemoryService memoryService,
            LLMService llmService,
            AgenticLoopRunner agenticLoopRunner,
            String defaultProjectPath) {
        this(toolRegistry, toolSelector, toolExecutor, memoryService, llmService, null,
                null, null, agenticLoopRunner, defaultProjectPath);
    }

    public Flux<AgentEvent> executeStream(ChatRequest request) {
        Sinks.Many<AgentEvent> sink = Sinks.many().multicast().onBackpressureBuffer();

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

        // Phase 0: Session setup
        memoryService.saveShortTerm(sessionId, "userMessage", userMessage);
        memoryService.saveShortTerm(sessionId, "projectPath", projectPath);

        // Ensure workspace is open for the current project (required for RAG retrieval)
        if (workspaceService != null) {
            try {
                workspaceService.openWorkspace(projectPath);
            } catch (Exception e) {
                // workspace open failure should not abort the agent
            }
        }

        // Phase 1: RAG Trigger (deterministic, independent of tool selection)
        String ragContext = "";
        if (codeQuestionDetector != null && codeQuestionDetector.needsRag(userMessage) && ragService != null) {
            ragContext = ragService.retrieveContext(userMessage);
            if (!ragContext.isBlank()) {
                sink.tryEmitNext(AgentEvent.ragRead(ragContext));
            }
        }

        // Phase 2: Route check → Agentic Loop or Direct Answer
        sink.tryEmitNext(AgentEvent.planning("Analyzing request..."));

        boolean isQuickChat = deterministicRouter != null && deterministicRouter.isQuickChat(userMessage);

        if (isQuickChat) {
            sink.tryEmitNext(AgentEvent.thinking("No tool needed, providing direct answer..."));
            String answer = generateDirectAnswer(userMessage, ragContext, request.getEffectiveHistory());
            sink.tryEmitNext(AgentEvent.finalAnswer(answer));
        } else if (agenticLoopRunner != null) {
            sink.tryEmitNext(AgentEvent.thinking("Available tools: " + toolRegistry.getToolNames()));
            String answer = agenticLoopRunner.run(userMessage, ragContext,
                    request.getEffectiveHistory(), sink);
            sink.tryEmitNext(AgentEvent.finalAnswer(answer));
        } else {
            // Fallback to deterministic ToolSelector path (for backward compatibility)
            sink.tryEmitNext(AgentEvent.thinking("Agentic loop unavailable, using direct answer..."));
            String answer = generateDirectAnswer(userMessage, ragContext, request.getEffectiveHistory());
            sink.tryEmitNext(AgentEvent.finalAnswer(answer));
        }

        // Final memory update (silent — no user-facing event)
        memoryService.saveShortTerm(sessionId, "endTime", System.currentTimeMillis());
    }

    private ToolExecutor.ToolExecutionResult executeWithSelfHealing(
            ToolSelector.ToolSelection selection,
            String userMessage,
            String projectPath,
            Sinks.Many<AgentEvent> sink,
            String ragContext) {

        String currentInput = selection.input();
        ToolExecutor.ToolExecutionResult lastResult = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            // Phase 2: Tool Execution
            sink.tryEmitNext(AgentEvent.toolCall(selection.toolName(), currentInput));

            ToolExecutor.ToolExecutionResult result = toolExecutor.execute(selection.toolName(), currentInput);

            if (result.success()) {
                sink.tryEmitNext(AgentEvent.toolResult(selection.toolName(), truncateResult(result.output(), 500)));
                sink.tryEmitNext(AgentEvent.verification("SUCCESS", "Tool executed successfully"));
                memoryService.saveExperience(selection.toolName(), result.output(), userMessage);
                return result;
            }

            // Execution failed - need self-healing
            lastResult = result;
            sink.tryEmitNext(AgentEvent.toolResult(selection.toolName(), "Error: " + result.error()));
            sink.tryEmitNext(AgentEvent.verification("FAILED", result.error()));

            if (attempt < MAX_RETRIES) {
                // Phase 2.5: Reflection - Analyze error and generate fix suggestions
                String reflection = performReflection(userMessage, selection.toolName(), currentInput, result.error());
                sink.tryEmitNext(AgentEvent.reflection(reflection));

                // Phase 2.6: Repair - Generate repair plan
                String repairPlan = generateRepairPlan(userMessage, selection.toolName(), result.error(), reflection);
                sink.tryEmitNext(AgentEvent.repair(repairPlan));

                // Determine new input for retry
                currentInput = extractRepairInput(repairPlan, currentInput, projectPath);

                sink.tryEmitNext(AgentEvent.retry(attempt + 1, MAX_RETRIES));

                // Save failure and repair strategy to memory
                memoryService.saveFailure(selection.toolName(), result.error(), repairPlan);
            } else {
                // Max retries exceeded
                sink.tryEmitNext(AgentEvent.maxRetriesExceeded(MAX_RETRIES));
                memoryService.saveFailure(selection.toolName(), result.error(), "Max retries exceeded");
            }
        }

        return lastResult != null ? lastResult : ToolExecutor.ToolExecutionResult.error("Unknown error");
    }

    private String performReflection(String userMessage, String toolName, String input, String error) {
        String prompt = """
                You are a self-healing agent analyzing a tool execution failure.

                User request: %s
                Tool used: %s
                Tool input: %s
                Error: %s

                Analyze the failure and provide:
                1. Root cause of the failure
                2. What went wrong
                3. Suggestions to fix the issue

                Be concise and specific. Focus on actionable insights.
                """.formatted(userMessage, toolName, input, error);

        LLMService.LLMResponse response = llmService.chat(prompt);
        return response.content() != null ? response.content() : "Unable to analyze failure.";
    }

    private String generateRepairPlan(String userMessage, String toolName, String error, String reflection) {
        String prompt = """
                You are a self-healing agent generating a repair plan.

                User request: %s
                Tool: %s
                Error: %s
                Reflection: %s

                Based on the error and reflection, generate a repair plan that includes:
                1. Modified approach to solve the problem
                2. Alternative tool or parameters if needed
                3. Specific fix to apply

                Return ONLY the repair plan, no extra commentary.
                """.formatted(userMessage, toolName, error, reflection);

        LLMService.LLMResponse response = llmService.chat(prompt);
        return response.content() != null ? response.content() : "No repair plan generated.";
    }

    private String extractRepairInput(String repairPlan, String originalInput, String projectPath) {
        // Try to extract improved input from repair plan
        String prompt = """
                Extract the improved tool input from this repair plan.

                Original input: %s
                Repair plan: %s

                Return ONLY the improved input to pass to the tool, nothing else.
                If no specific input is mentioned, return the original input.
                """.formatted(originalInput, repairPlan);

        LLMService.LLMResponse response = llmService.chat(prompt);
        String extracted = response.content();

        if (extracted == null || extracted.isBlank() || extracted.toLowerCase().contains("no specific input")) {
            return originalInput;
        }

        // If LLM returned something that looks like a path, prepend project path if needed
        if (!extracted.startsWith("/") && !extracted.contains(" ")) {
            return projectPath + "/" + extracted;
        }

        return extracted;
    }

    private String generateDirectAnswer(String userMessage, String ragContext,
                                          java.util.List<com.aicoding.agent.dto.ChatRequest.HistoryMessage> history) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a helpful coding assistant.\n\n");

        // Include conversation history so follow-up questions have context
        if (history != null && !history.isEmpty()) {
            prompt.append("Previous conversation:\n");
            for (var msg : history) {
                String label = "user".equalsIgnoreCase(msg.role()) ? "User" : "Assistant";
                prompt.append(label).append(": ").append(msg.content()).append("\n");
            }
            prompt.append("\n");
        }

        if (ragContext != null && !ragContext.isBlank()) {
            prompt.append("""
                    Here are relevant code snippets from the workspace (with file path and line numbers):

                    %s

                    Use these snippets to answer accurately. Reference paths and line numbers when relevant.

                    Current question: %s
                    """.formatted(ragContext, userMessage));
        } else {
            prompt.append("Current question: ").append(userMessage).append("\n\n");
            prompt.append("Please provide a direct answer without using any tools.");
            if (history != null && !history.isEmpty()) {
                prompt.append(" Take into account the conversation context above.");
            }
        }

        LLMService.LLMResponse response = llmService.chat(prompt.toString());
        return stripThinking(response.content() != null ? response.content() : "I couldn't generate a response.");
    }

    private String stripThinking(String text) {
        if (text == null) return "";
        return text.replaceAll("(?s)<think>.*?</think>", "").trim();
    }

    private String buildSummary(String userMessage, ToolSelector.ToolSelection selection, ToolExecutor.ToolExecutionResult result, String ragContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Execution Summary ===\n\n");
        sb.append("User request: ").append(userMessage).append("\n\n");
        sb.append("Tool selected: ").append(selection.toolName()).append("\n");
        sb.append("Status: ").append(result.success() ? "SUCCESS" : "FAILED").append("\n");
        if (result.success()) {
            sb.append("\nOutput preview:\n").append(truncateResult(result.output(), 500));
        } else {
            sb.append("\nError: ").append(result.error());
        }
        if (ragContext != null && !ragContext.isBlank()) {
            sb.append("\n\n--- Related Code ---\n").append(truncateResult(ragContext, 500));
        }
        return sb.toString();
    }

    private String truncateResult(String result, int maxLen) {
        if (result == null) return "null";
        if (result.length() <= maxLen) return result;
        return result.substring(0, maxLen) + "...";
    }
}
