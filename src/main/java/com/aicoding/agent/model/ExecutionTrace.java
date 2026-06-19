package com.aicoding.agent.model;

import java.time.LocalDateTime;

public class ExecutionTrace {
    private int stepId;
    private String phase;           // GOAL_ENHANCEMENT, PLANNING, EXECUTION, VERIFICATION, FAILURE_ANALYSIS, RECOVERY
    private String input;
    private String toolUsed;
    private String output;
    private String reasoning;
    private LocalDateTime timestamp;
    private boolean success;

    public ExecutionTrace() {
        this.timestamp = LocalDateTime.now();
    }

    public ExecutionTrace(int stepId, String phase, String input, String toolUsed, String output, String reasoning, boolean success) {
        this.stepId = stepId;
        this.phase = phase;
        this.input = input;
        this.toolUsed = toolUsed;
        this.output = output;
        this.reasoning = reasoning;
        this.success = success;
        this.timestamp = LocalDateTime.now();
    }

    public int getStepId() { return stepId; }
    public void setStepId(int stepId) { this.stepId = stepId; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public String getInput() { return input; }
    public void setInput(String input) { this.input = input; }
    public String getToolUsed() { return toolUsed; }
    public void setToolUsed(String toolUsed) { this.toolUsed = toolUsed; }
    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }
    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
}
