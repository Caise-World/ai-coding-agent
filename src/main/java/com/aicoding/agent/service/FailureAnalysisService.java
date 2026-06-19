package com.aicoding.agent.service;

import com.aicoding.agent.model.FailureAnalysis;
import com.aicoding.agent.model.SubTask;
import org.springframework.stereotype.Service;

@Service
public class FailureAnalysisService {

    private final LLMService llmService;

    public FailureAnalysisService(LLMService llmService) {
        this.llmService = llmService;
    }

    public FailureAnalysis analyze(SubTask failedTask) {
        FailureAnalysis analysis = new FailureAnalysis();
        analysis.setTaskDescription(failedTask.getDescription());
        analysis.setTaskResult(failedTask.getResult());

        String prompt = buildAnalysisPrompt(failedTask);
        LLMService.LLMResponse response = llmService.chat(prompt);

        if (response.error() != null || response.content() == null) {
            // Fall back to simple analysis
            simpleAnalysis(analysis, failedTask);
            return analysis;
        }

        parseAnalysis(analysis, response.content());
        analysis.setAnalyzed(true);
        return analysis;
    }

    private String buildAnalysisPrompt(SubTask task) {
        return """
                Analyze the following failed task and provide a structured failure analysis.

                Task: %s
                Tool used: %s
                Input: %s
                Result: %s

                Respond ONLY with a JSON object in this format:
                {
                  "failureReason": "What went wrong",
                  "rootCauseHypothesis": "Why it happened",
                  "fixStrategy": "How to fix it"
                }

                Output ONLY the JSON object, no other text.
                """.formatted(
                    task.getDescription(),
                    task.getTool(),
                    task.getInput(),
                    task.getResult() != null ? task.getResult() : "No result"
                );
    }

    private void parseAnalysis(FailureAnalysis analysis, String content) {
        try {
            // Simple JSON parsing
            String lower = content.toLowerCase();

            // Extract failure reason
            int reasonStart = lower.indexOf("failurereason");
            if (reasonStart >= 0) {
                int colon = content.indexOf(":", reasonStart);
                int nextField = content.indexOf("\"", colon + 2);
                if (colon > 0 && nextField > colon) {
                    analysis.setFailureReason(content.substring(colon + 1, nextField).trim());
                }
            }

            // Extract root cause
            int causeStart = lower.indexOf("rootcausehypothesis");
            if (causeStart >= 0) {
                int colon = content.indexOf(":", causeStart);
                int nextField = content.indexOf("\"", colon + 2);
                if (colon > 0 && nextField > colon) {
                    analysis.setRootCauseHypothesis(content.substring(colon + 1, nextField).trim());
                }
            }

            // Extract fix strategy
            int fixStart = lower.indexOf("fixstrategy");
            if (fixStart >= 0) {
                int colon = content.indexOf(":", fixStart);
                int nextField = content.indexOf("\"", colon + 2);
                if (colon > 0 && nextField > colon) {
                    analysis.setFixStrategy(content.substring(colon + 1, nextField).trim());
                }
            }

            if (analysis.getFailureReason() == null) {
                simpleAnalysis(analysis, new SubTask());
            }
        } catch (Exception e) {
            simpleAnalysis(analysis, new SubTask());
        }
    }

    private void simpleAnalysis(FailureAnalysis analysis, SubTask task) {
        String result = task.getResult() != null ? task.getResult().toLowerCase() : "";

        if (result.contains("not found")) {
            analysis.setFailureReason("Resource not found");
            analysis.setRootCauseHypothesis("The specified resource path is incorrect or doesn't exist");
            analysis.setFixStrategy("Verify the path is correct and the resource exists");
        } else if (result.contains("error") || result.contains("exception")) {
            analysis.setFailureReason("Execution error");
            analysis.setRootCauseHypothesis("An error occurred during task execution");
            analysis.setFixStrategy("Check error details and fix the underlying issue");
        } else if (result.contains("timeout")) {
            analysis.setFailureReason("Operation timeout");
            analysis.setRootCauseHypothesis("The operation took too long to complete");
            analysis.setFixStrategy("Increase timeout or simplify the operation");
        } else {
            analysis.setFailureReason("Task did not complete successfully");
            analysis.setRootCauseHypothesis("Unknown reason based on result");
            analysis.setFixStrategy("Review task output and retry with adjusted parameters");
        }

        analysis.setAnalyzed(true);
    }
}
