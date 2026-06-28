package com.aicoding.agent.agent;

import com.aicoding.agent.dto.AgentEvent;
import com.aicoding.agent.dto.ChatRequest;
import com.aicoding.agent.registry.ToolExecutor;
import com.aicoding.agent.registry.ToolRegistry;
import com.aicoding.agent.service.LLMService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;

@Component
public class AgenticLoopRunner {

    private static final Logger log = LoggerFactory.getLogger(AgenticLoopRunner.class);
    private static final int MAX_ROUNDS = 5;

    private final LLMService llmService;
    private final ToolExecutor toolExecutor;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    public AgenticLoopRunner(LLMService llmService, ToolExecutor toolExecutor, ToolRegistry toolRegistry) {
        this.llmService = llmService;
        this.toolExecutor = toolExecutor;
        this.toolRegistry = toolRegistry;
        this.objectMapper = new ObjectMapper();
    }

    public String run(
            String userMessage,
            String ragContext,
            List<ChatRequest.HistoryMessage> history,
            Sinks.Many<AgentEvent> sink
    ) {
        String systemPrompt = buildSystemPrompt();

        List<LLMService.Message> messages = new ArrayList<>();
        messages.add(new LLMService.Message("system", systemPrompt));

        if (history != null) {
            for (var msg : history) {
                messages.add(new LLMService.Message(msg.role(), msg.content()));
            }
        }

        String userContent = userMessage;
        if (ragContext != null && !ragContext.isBlank()) {
            userContent = "Relevant code from the workspace (with file paths and line numbers):\n\n"
                    + ragContext + "\n\nUser question: " + userMessage;
        }
        messages.add(new LLMService.Message("user", userContent));

        for (int round = 1; round <= MAX_ROUNDS; round++) {
            sink.tryEmitNext(AgentEvent.thinking("Round " + round + "/" + MAX_ROUNDS));

            LLMService.LLMResponse response = llmService.chat(messages);

            if (response.error() != null) {
                log.error("LLM error in agentic loop round {}: {}", round, response.error());
                return "I encountered an error: " + response.error();
            }

            String rawContent = response.content();
            if (rawContent == null || rawContent.isBlank()) {
                return "I received an empty response.";
            }

            rawContent = rawContent.replaceAll("(?s)<think>.*?</think>", "").trim();

            JsonNode action = parseAction(rawContent);

            if (action == null) {
                log.warn("Failed to parse action JSON in round {}, treating as final answer", round);
                return rawContent;
            }

            String actionType = action.has("action") ? action.get("action").asText() : "";

            if ("final_answer".equals(actionType)) {
                return action.has("content") ? action.get("content").asText() : rawContent;
            }

            if ("tool_call".equals(actionType)) {
                String toolName = action.has("tool_name") ? action.get("tool_name").asText() : null;
                String toolInput = action.has("tool_input") ? action.get("tool_input").asText() : "";
                String reasoning = action.has("reasoning") ? action.get("reasoning").asText() : "";

                if (toolName == null) {
                    messages.add(new LLMService.Message("user",
                            "Error: tool_call requires a tool_name field. Please specify which tool to use."));
                    continue;
                }

                if (reasoning != null && !reasoning.isBlank()) {
                    sink.tryEmitNext(AgentEvent.thinking(reasoning));
                }
                sink.tryEmitNext(AgentEvent.toolCall(toolName, toolInput));

                ToolExecutor.ToolExecutionResult result = toolExecutor.execute(toolName, toolInput);

                messages.add(new LLMService.Message("assistant", rawContent));

                if (result.success()) {
                    String output = truncate(result.output(), 3000);
                    sink.tryEmitNext(AgentEvent.toolResult(toolName, truncate(output, 500)));
                    messages.add(new LLMService.Message("user",
                            "Tool result for " + toolName + ":\n" + output
                            + "\n\nContinue. Call another tool or provide final_answer."));
                } else {
                    sink.tryEmitNext(AgentEvent.toolResult(toolName, "Error: " + result.error()));
                    messages.add(new LLMService.Message("user",
                            "Tool " + toolName + " failed: " + result.error()
                            + "\n\nAdjust your approach and continue."));
                }
            } else {
                log.warn("Unknown action type '{}' in round {}, treating as final answer", actionType, round);
                return rawContent;
            }
        }

        messages.add(new LLMService.Message("user",
                "Maximum tool calls reached. Provide your final_answer now based on what you have."));
        LLMService.LLMResponse finalResponse = llmService.chat(messages);
        String content = finalResponse.content();
        if (content != null && !content.isBlank()) {
            String stripped = content.replaceAll("(?s)<think>.*?</think>", "").trim();
            // Try to parse as JSON one more time in case LLM still outputs action format
            JsonNode lastAction = parseAction(stripped);
            if (lastAction != null && "final_answer".equals(lastAction.has("action") ? lastAction.get("action").asText() : "")) {
                return lastAction.has("content") ? lastAction.get("content").asText() : stripped;
            }
            return stripped;
        }
        return "I wasn't able to complete the task within the allowed steps.";
    }

    private String buildSystemPrompt() {
        return """
                You are an AI coding assistant with access to tools. Help users with programming tasks.

                ## Available Tools
                %s

                ## Output Format
                You MUST respond with valid JSON only. No other text before or after, no markdown fences.

                To use a tool:
                {"action":"tool_call","tool_name":"ToolName","tool_input":"the input to pass","reasoning":"why I need this tool"}

                To give your final answer:
                {"action":"final_answer","content":"Your complete answer in markdown"}

                ## Rules
                1. Think step by step. Use tools to gather information before answering.
                2. One tool call per response. You'll see the result and can call another.
                3. When you have enough information, output final_answer.
                4. If the question doesn't need tools, answer directly with final_answer.
                5. Maximum 5 tool calls total. Be efficient.
                6. Always read files before editing them.
                7. Use GrepTool to search code, FileReadTool to read files, ProjectScanTool to see structure.
                """.formatted(toolRegistry.getToolsJsonForLLM());
    }

    private JsonNode parseAction(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ignored) {}

        String cleaned = raw
                .replaceAll("^```json\\s*", "")
                .replaceAll("^```\\s*", "")
                .replaceAll("```\\s*$", "")
                .trim();

        try {
            return objectMapper.readTree(cleaned);
        } catch (Exception e) {
            log.warn("Failed to parse action JSON: {}", e.getMessage());
            return null;
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }
}
