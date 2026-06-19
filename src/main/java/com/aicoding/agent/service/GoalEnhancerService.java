package com.aicoding.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GoalEnhancerService {

    private final LLMService llmService;
    private final ObjectMapper objectMapper;
    private final Pattern GOAL_PATTERN = Pattern.compile(
        "-?\\s*\\[?\\s*\\d+[.、)\\]]?\\s*(.+?)(?=\\n|$)",
        Pattern.CASE_INSENSITIVE);

    public GoalEnhancerService(LLMService llmService) {
        this.llmService = llmService;
        this.objectMapper = new ObjectMapper();
    }

    public List<String> enhance(String userMessage, String projectPath) {
        String prompt = buildEnhancementPrompt(userMessage, projectPath);
        LLMService.LLMResponse response = llmService.chat(prompt);

        if (response.error() != null) {
            return List.of(userMessage);
        }

        return parseGoals(response.content(), userMessage);
    }

    private String buildEnhancementPrompt(String userMessage, String projectPath) {
        return """
                You are a goal enhancement assistant. Given a user request, expand it into a complete list of goals.

                User request: %s
                Project path: %s

                Analyze the request and expand it to include implicit goals. For example:
                - "fix compile error" → ["Analyze compile error", "Fix the error", "Verify build succeeds", "Run tests"]
                - "analyze project" → ["Scan project structure", "Read key files", "Summarize findings"]

                Respond ONLY with a JSON array of goal strings:
                ["goal 1", "goal 2", "goal 3", ...]

                Maximum 6 goals. Output ONLY the JSON array, no other text.
                """.formatted(userMessage, projectPath);
    }

    private List<String> parseGoals(String content, String defaultGoal) {
        List<String> goals = new ArrayList<>();

        if (content == null || content.isBlank()) {
            return List.of(defaultGoal);
        }

        // Try JSON array format first
        try {
            JsonNode root = objectMapper.readTree(content);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    goals.add(node.asText());
                }
                if (!goals.isEmpty()) {
                    return goals;
                }
            }
        } catch (Exception e) {
            // Fall through to regex parsing
        }

        // Fall back to line-by-line parsing
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // Remove bullet points, numbers, brackets
            line = line.replaceAll("^[-*•]\\s*", "");
            line = line.replaceAll("^\\d+[.、)\\]]\\s*", "");
            line = line.replaceAll("^\"", "").replaceAll("\"$", "");

            if (!line.isEmpty() && line.length() > 2) {
                goals.add(line);
            }
        }

        if (goals.isEmpty()) {
            return List.of(defaultGoal);
        }

        return goals;
    }
}
