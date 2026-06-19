package com.aicoding.agent.service;

import com.aicoding.agent.model.SubTask;
import com.aicoding.agent.model.TaskState;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskVerifierService {

    private final LLMService llmService;

    public TaskVerifierService(LLMService llmService) {
        this.llmService = llmService;
    }

    public boolean verify(SubTask task) {
        String verificationPrompt = buildVerificationPrompt(task);
        LLMService.LLMResponse response = llmService.chat(verificationPrompt);

        if (response.error() != null) {
            // If LLM fails, do simple verification
            return simpleVerify(task);
        }

        String content = response.content().toLowerCase();
        return content.contains("success") || content.contains("passed") ||
               content.contains("completed") || content.contains("verified");
    }

    private boolean simpleVerify(SubTask task) {
        String result = task.getResult();
        if (result == null || result.isBlank()) {
            return false;
        }

        // Check for error indicators
        String lowerResult = result.toLowerCase();
        if (lowerResult.contains("error") && !lowerResult.contains("no error")) {
            return false;
        }
        if (lowerResult.contains("failed") && !lowerResult.contains("not failed")) {
            return false;
        }
        if (lowerResult.contains("exception")) {
            return false;
        }
        if (lowerResult.contains("exit code:") && lowerResult.contains("1")) {
            return false;
        }

        // Check for success indicators
        return lowerResult.contains("success") ||
               lowerResult.contains("build success") ||
               lowerResult.contains("exit code: 0") ||
               lowerResult.contains("completed") ||
               result.length() > 10;
    }

    public boolean verifyAll(List<SubTask> tasks) {
        for (SubTask task : tasks) {
            if (task.getState() != TaskState.SUCCESS) {
                return false;
            }
        }
        return true;
    }

    public List<SubTask> getFailedTasks(List<SubTask> tasks) {
        return tasks.stream()
                .filter(t -> t.getState() == TaskState.FAILED || t.getState() == TaskState.NEED_RETRY)
                .toList();
    }

    private String buildVerificationPrompt(SubTask task) {
        return """
                Verify if the following task was completed successfully.

                Task: %s
                Tool: %s
                Input: %s
                Result: %s

                Respond with ONLY one word:
                - "SUCCESS" if the task was completed successfully
                - "FAILED" if the task failed or didn't achieve its goal

                Be strict in your verification.
                """.formatted(
                    task.getDescription(),
                    task.getTool(),
                    task.getInput(),
                    task.getResult() != null ? task.getResult() : "No result"
                );
    }
}
