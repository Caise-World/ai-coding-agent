package com.aicoding.agent.model;

public class FailureAnalysis {
    private String taskDescription;
    private String taskResult;
    private String failureReason;
    private String rootCauseHypothesis;
    private String fixStrategy;
    private boolean analyzed;

    public FailureAnalysis() {
        this.analyzed = false;
    }

    public String getTaskDescription() { return taskDescription; }
    public void setTaskDescription(String taskDescription) { this.taskDescription = taskDescription; }
    public String getTaskResult() { return taskResult; }
    public void setTaskResult(String taskResult) { this.taskResult = taskResult; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public String getRootCauseHypothesis() { return rootCauseHypothesis; }
    public void setRootCauseHypothesis(String rootCauseHypothesis) { this.rootCauseHypothesis = rootCauseHypothesis; }
    public String getFixStrategy() { return fixStrategy; }
    public void setFixStrategy(String fixStrategy) { this.fixStrategy = fixStrategy; }
    public boolean isAnalyzed() { return analyzed; }
    public void setAnalyzed(boolean analyzed) { this.analyzed = analyzed; }
}
