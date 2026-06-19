package com.aicoding.agent.service;

import com.aicoding.agent.dto.AgentEvent;
import com.aicoding.agent.dto.ChatRequest;
import com.aicoding.agent.memory.MemoryService;
import com.aicoding.agent.model.*;
import com.aicoding.agent.registry.ToolExecutor;
import com.aicoding.agent.registry.ToolRegistry;
import com.aicoding.agent.registry.ToolSelector;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class PluginBasedStreamingAgentService {

    private final ToolRegistry toolRegistry;
    private final ToolSelector toolSelector;
    private final ToolExecutor toolExecutor;
    private final MemoryService memoryService;
    private final LLMService llmService;
    private final String defaultProjectPath;

    public PluginBasedStreamingAgentService(
            ToolRegistry toolRegistry,
            ToolSelector toolSelector,
            ToolExecutor toolExecutor,
            MemoryService memoryService,
            LLMService llmService,
            String defaultProjectPath) {
        this.toolRegistry = toolRegistry;
        this.toolSelector = toolSelector;
        this.toolExecutor = toolExecutor;
        this.memoryService = memoryService;
        this.llmService = llmService;
        this.defaultProjectPath = defaultProjectPath;
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

        // Phase 0: Memory Retrieval
        sink.tryEmitNext(AgentEvent.thinking("Retrieving relevant experiences from memory..."));
        String memoryContext = memoryService.getMemoryContext();
        sink.tryEmitNext(AgentEvent.memoryRead(memoryContext.isBlank() ? "No prior experience found" : memoryContext));

        memoryService.saveShortTerm(sessionId, "userMessage", userMessage);
        memoryService.saveShortTerm(sessionId, "projectPath", projectPath);
        sink.tryEmitNext(AgentEvent.memoryWrite("Saved user request to short-term memory"));

        // Phase 1: Tool Selection (LLM decides which tool to use)
        sink.tryEmitNext(AgentEvent.planning("Analyzing request and selecting appropriate tool..."));
        sink.tryEmitNext(AgentEvent.thinking("Available tools: " + toolRegistry.getToolNames()));

        ToolSelector.ToolSelection selection = toolSelector.select(userMessage, projectPath);
        sink.tryEmitNext(AgentEvent.thinking("LLM selected tool: " + selection.toolName()));

        if (selection.hasError()) {
            sink.tryEmitNext(AgentEvent.error("Tool selection failed: " + selection.error()));
            sink.tryEmitNext(AgentEvent.finalAnswer("Sorry, I couldn't select an appropriate tool for your request."));
            return;
        }

        if (selection.isNone()) {
            sink.tryEmitNext(AgentEvent.thinking("No tool needed, providing direct answer..."));
            String answer = generateDirectAnswer(userMessage);
            sink.tryEmitNext(AgentEvent.finalAnswer(answer));
            return;
        }

        // Phase 2: Tool Execution via ToolExecutor
        sink.tryEmitNext(AgentEvent.toolCall(selection.toolName(), selection.input()));

        ToolExecutor.ToolExecutionResult result = toolExecutor.execute(selection.toolName(), selection.input());

        if (result.success()) {
            sink.tryEmitNext(AgentEvent.toolResult(selection.toolName(), truncateResult(result.output(), 500)));
            sink.tryEmitNext(AgentEvent.verification("SUCCESS", "Tool executed successfully"));
            memoryService.saveExperience(selection.toolName(), result.output(), userMessage);
        } else {
            sink.tryEmitNext(AgentEvent.toolResult(selection.toolName(), "Error: " + result.error()));
            sink.tryEmitNext(AgentEvent.verification("FAILED", result.error()));
            memoryService.saveFailure(selection.toolName(), result.error(), userMessage);
        }

        // Phase 3: Generate Final Answer
        String summary = buildSummary(userMessage, selection, result);
        sink.tryEmitNext(AgentEvent.finalAnswer(summary));

        // Final memory update
        memoryService.saveShortTerm(sessionId, "endTime", System.currentTimeMillis());
        memoryService.saveShortTerm(sessionId, "selectedTool", selection.toolName());
        sink.tryEmitNext(AgentEvent.memoryWrite("Session completed. Tool: " + selection.toolName()));
    }

    private String generateDirectAnswer(String userMessage) {
        String prompt = """
                You are a helpful coding assistant. The user asked:

                %s

                Please provide a direct answer without using any tools.
                """.formatted(userMessage);

        LLMService.LLMResponse response = llmService.chat(prompt);
        return response.content() != null ? response.content() : "I couldn't generate a response.";
    }

    private String buildSummary(String userMessage, ToolSelector.ToolSelection selection, ToolExecutor.ToolExecutionResult result) {
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
        return sb.toString();
    }

    private String truncateResult(String result, int maxLen) {
        if (result == null) return "null";
        if (result.length() <= maxLen) return result;
        return result.substring(0, maxLen) + "...";
    }
}
