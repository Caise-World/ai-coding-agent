package com.aicoding.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class LLMService {

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final String fallbackApiKey;
    private final String fallbackModel;
    private final String fallbackBaseUrl;
    private final ObjectMapper objectMapper;

    public LLMService(
            RestTemplate restTemplate,
            @Value("${llm.api-key}") String apiKey,
            @Value("${llm.model}") String model,
            @Value("${llm.base-url}") String baseUrl,
            @Value("${llm.fallback.api-key}") String fallbackApiKey,
            @Value("${llm.fallback.model}") String fallbackModel,
            @Value("${llm.fallback.base-url}") String fallbackBaseUrl) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.fallbackApiKey = fallbackApiKey;
        this.fallbackModel = fallbackModel;
        this.fallbackBaseUrl = fallbackBaseUrl;
        this.objectMapper = new ObjectMapper();
    }

    public LLMResponse chat(String prompt) {
        LLMResponse primary = call(baseUrl, apiKey, model, prompt);
        if (primary.error() == null) {
            return primary;
        }

        if (fallbackApiKey == null || fallbackApiKey.isBlank()) {
            return primary;
        }

        LLMResponse fallback = call(fallbackBaseUrl, fallbackApiKey, fallbackModel, prompt);
        if (fallback.error() == null) {
            return fallback;
        }

        return new LLMResponse(null,
                "Primary: " + primary.error() + " | Fallback: " + fallback.error());
    }

    private LLMResponse call(String url, String key, String modelName, String prompt) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(key);

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", modelName);

            ArrayNode messages = objectMapper.createArrayNode();
            ObjectNode userMsg = objectMapper.createObjectNode();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);
            messages.add(userMsg);

            requestBody.set("messages", messages);

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);

            String fullUrl = url + "/chat/completions";
            ResponseEntity<String> response = restTemplate.exchange(fullUrl, HttpMethod.POST, entity, String.class);

            return parseResponse(response.getBody());
        } catch (Exception e) {
            return new LLMResponse(null, "Error: " + e.getMessage());
        }
    }

    private LLMResponse parseResponse(String responseBody) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode choices = root.get("choices");

        if (choices != null && choices.isArray() && choices.size() > 0) {
            JsonNode choice = choices.get(0);
            JsonNode message = choice.get("message");

            String content = message.has("content") && !message.get("content").isNull()
                ? message.get("content").asText() : null;

            return new LLMResponse(content, null);
        }

        return new LLMResponse(null, "No response from LLM");
    }

    public record LLMResponse(String content, String error) {}
}
