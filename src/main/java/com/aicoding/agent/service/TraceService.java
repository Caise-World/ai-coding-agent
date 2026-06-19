package com.aicoding.agent.service;

import com.aicoding.agent.model.ExecutionTrace;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TraceService {

    // In-memory trace storage
    private final Map<String, List<ExecutionTrace>> sessionTraces;
    private String currentSessionId;

    public TraceService() {
        this.sessionTraces = new ConcurrentHashMap<>();
    }

    public void startSession(String sessionId) {
        this.currentSessionId = sessionId;
        sessionTraces.put(sessionId, new ArrayList<>());
    }

    public void addTrace(ExecutionTrace trace) {
        if (currentSessionId == null) {
            startSession(UUID.randomUUID().toString());
        }
        List<ExecutionTrace> traces = sessionTraces.get(currentSessionId);
        if (traces != null) {
            traces.add(trace);
        }
    }

    public void addTrace(int stepId, String phase, String input, String toolUsed, String output, String reasoning, boolean success) {
        ExecutionTrace trace = new ExecutionTrace(stepId, phase, input, toolUsed, output, reasoning, success);
        addTrace(trace);
    }

    public List<ExecutionTrace> getCurrentTraces() {
        return sessionTraces.getOrDefault(currentSessionId, new ArrayList<>());
    }

    public List<ExecutionTrace> getTraces(String sessionId) {
        return sessionTraces.getOrDefault(sessionId, new ArrayList<>());
    }

    public String generateTraceReport() {
        List<ExecutionTrace> traces = getCurrentTraces();
        if (traces.isEmpty()) {
            return "No traces available";
        }

        StringBuilder report = new StringBuilder();
        report.append("=== Execution Trace Report ===\n\n");

        for (ExecutionTrace trace : traces) {
            report.append(String.format("[Step %d] %s - %s\n",
                    trace.getStepId(),
                    trace.getPhase(),
                    trace.isSuccess() ? "SUCCESS" : "FAILED"));
            report.append("  Input: ").append(truncate(trace.getInput(), 80)).append("\n");
            if (trace.getToolUsed() != null) {
                report.append("  Tool: ").append(trace.getToolUsed()).append("\n");
            }
            if (trace.getReasoning() != null) {
                report.append("  Reasoning: ").append(truncate(trace.getReasoning(), 80)).append("\n");
            }
            report.append("  Output: ").append(truncate(trace.getOutput(), 100)).append("\n");
            report.append("  Time: ").append(trace.getTimestamp()).append("\n");
            report.append("\n");
        }

        return report.toString();
    }

    public List<ExecutionTrace> replay(String sessionId) {
        return getTraces(sessionId);
    }

    public boolean compareTraces(String sessionId1, String sessionId2) {
        List<ExecutionTrace> traces1 = getTraces(sessionId1);
        List<ExecutionTrace> traces2 = getTraces(sessionId2);

        if (traces1.size() != traces2.size()) {
            return false;
        }

        for (int i = 0; i < traces1.size(); i++) {
            ExecutionTrace t1 = traces1.get(i);
            ExecutionTrace t2 = traces2.get(i);

            if (!t1.getPhase().equals(t2.getPhase()) ||
                !Objects.equals(t1.getToolUsed(), t2.getToolUsed()) ||
                t1.isSuccess() != t2.isSuccess()) {
                return false;
            }
        }

        return true;
    }

    public void clearSession(String sessionId) {
        sessionTraces.remove(sessionId);
    }

    public void clearCurrentSession() {
        if (currentSessionId != null) {
            clearSession(currentSessionId);
        }
    }

    public String getCurrentSessionId() {
        return currentSessionId;
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return "null";
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen) + "...";
    }
}
