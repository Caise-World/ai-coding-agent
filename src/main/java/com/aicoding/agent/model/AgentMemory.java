package com.aicoding.agent.model;

import java.util.*;

public class AgentMemory {
    // Short-term memory: current task state
    private Map<String, Object> currentTaskState;

    // Long-term memory: solved problem patterns
    private List<ProblemPattern> solvedPatterns;

    public AgentMemory() {
        this.currentTaskState = new HashMap<>();
        this.solvedPatterns = new ArrayList<>();
    }

    public void updateTaskState(String key, Object value) {
        currentTaskState.put(key, value);
    }

    public Object getTaskState(String key) {
        return currentTaskState.get(key);
    }

    public void clearTaskState() {
        currentTaskState.clear();
    }

    public void addSolvedPattern(String problemType, String solution, String context) {
        solvedPatterns.add(new ProblemPattern(problemType, solution, context));
    }

    public Optional<ProblemPattern> findSimilarPattern(String problemType) {
        return solvedPatterns.stream()
                .filter(p -> p.getProblemType().equalsIgnoreCase(problemType))
                .findFirst();
    }

    public Map<String, Object> getCurrentTaskState() { return currentTaskState; }
    public List<ProblemPattern> getSolvedPatterns() { return solvedPatterns; }

    public static class ProblemPattern {
        private String problemType;
        private String solution;
        private String context;
        private long timestamp;

        public ProblemPattern(String problemType, String solution, String context) {
            this.problemType = problemType;
            this.solution = solution;
            this.context = context;
            this.timestamp = System.currentTimeMillis();
        }

        public String getProblemType() { return problemType; }
        public String getSolution() { return solution; }
        public String getContext() { return context; }
        public long getTimestamp() { return timestamp; }
    }
}
