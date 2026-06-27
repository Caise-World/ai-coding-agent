package com.aicoding.agent.dto;

import java.time.LocalDateTime;

public class AgentEvent {
    private String type;
    private String content;
    private String toolName;
    private String input;
    private LocalDateTime timestamp;

    public AgentEvent() {
        this.timestamp = LocalDateTime.now();
    }

    public AgentEvent(String type, String content) {
        this.type = type;
        this.content = content;
        this.timestamp = LocalDateTime.now();
    }

    public AgentEvent(String type, String toolName, String content) {
        this.type = type;
        this.toolName = toolName;
        this.content = content;
        this.timestamp = LocalDateTime.now();
    }

    public static AgentEvent planning(String content) {
        return new AgentEvent("PLANNING", content);
    }

    public static AgentEvent thinking(String content) {
        return new AgentEvent("THINKING", content);
    }

    public static AgentEvent toolCall(String toolName, String input) {
        AgentEvent event = new AgentEvent("TOOL_CALL", toolName, null);
        event.setInput(truncate(input, 200));
        return event;
    }

    public static AgentEvent toolResult(String toolName, String result) {
        return new AgentEvent("TOOL_RESULT", toolName, truncate(result, 500));
    }

    public static AgentEvent memoryRead(String content) {
        return new AgentEvent("MEMORY_READ", content);
    }

    public static AgentEvent memoryWrite(String content) {
        return new AgentEvent("MEMORY_WRITE", content);
    }

    public static AgentEvent verification(String success, String details) {
        return new AgentEvent("VERIFICATION", "[" + success + "] " + details);
    }

    public static AgentEvent failureAnalysis(String content) {
        return new AgentEvent("FAILURE_ANALYSIS", content);
    }

    public static AgentEvent recovery(String content) {
        return new AgentEvent("RECOVERY", content);
    }

    public static AgentEvent finalAnswer(String content) {
        return new AgentEvent("FINAL", content);
    }

    public static AgentEvent error(String content) {
        return new AgentEvent("ERROR", content);
    }

    public static AgentEvent reflection(String content) {
        return new AgentEvent("REFLECTION", content);
    }

    public static AgentEvent repair(String content) {
        return new AgentEvent("REPAIR", content);
    }

    public static AgentEvent retry(int attempt, int maxAttempts) {
        return new AgentEvent("RETRY", String.format("Retry attempt %d/%d", attempt, maxAttempts));
    }

    public static AgentEvent maxRetriesExceeded(int maxAttempts) {
        return new AgentEvent("MAX_RETRIES_EXCEEDED", String.format("Exceeded maximum retry attempts (%d)", maxAttempts));
    }

    public static AgentEvent ragRead(String content) {
        return new AgentEvent("RAG_READ", truncate(content, 2000));
    }

    private static String truncate(String str, int maxLen) {
        if (str == null) return "null";
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen) + "...";
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getInput() { return input; }
    public void setInput(String input) { this.input = input; }
}
