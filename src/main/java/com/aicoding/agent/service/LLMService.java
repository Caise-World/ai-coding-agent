package com.aicoding.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class LLMService {

    private static final Logger log = LoggerFactory.getLogger(LLMService.class);
    private static final long LLM_CALL_TIMEOUT_SECONDS = 60;

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

    public record Message(String role, String content) {}

    public record LLMResponse(String content, String error) {}

    // ─── Multi-message chat (for agentic loop) ──────────────────

    public LLMResponse chat(List<Message> messages) {
        log.info("LLM chat called with {} messages", messages.size());
        LLMResponse primary = callWithTimeout(baseUrl, apiKey, model, messages);
        if (primary.error() == null) {
            log.info("Primary LLM returned successfully, content length: {}",
                    primary.content() != null ? primary.content().length() : 0);
            return primary;
        }

        log.warn("Primary LLM failed: {}", primary.error());

        if (fallbackApiKey == null || fallbackApiKey.isBlank()) {
            log.warn("No fallback API key configured, returning primary error");
            return primary;
        }

        log.info("Trying fallback LLM...");
        LLMResponse fallback = callWithTimeout(fallbackBaseUrl, fallbackApiKey, fallbackModel, messages);
        if (fallback.error() == null) {
            log.info("Fallback LLM returned successfully, content length: {}",
                    fallback.content() != null ? fallback.content().length() : 0);
            return fallback;
        }

        log.error("Both primary and fallback LLMs failed");
        return new LLMResponse(null,
                "Primary: " + primary.error() + " | Fallback: " + fallback.error());
    }

    private LLMResponse callWithTimeout(String url, String key, String modelName, List<Message> messages) {
        try {
            return CompletableFuture
                    .supplyAsync(() -> call(url, key, modelName, messages))
                    .get(LLM_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.error("LLM call timed out after {}s to {}", LLM_CALL_TIMEOUT_SECONDS, url);
            return new LLMResponse(null, "Error: LLM call timed out after " + LLM_CALL_TIMEOUT_SECONDS + "s");
        } catch (Exception e) {
            log.error("LLM call failed with exception: {}", e.getMessage());
            return new LLMResponse(null, "Error: " + e.getMessage());
        }
    }

    private LLMResponse call(String url, String key, String modelName, List<Message> messages) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(key);

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", modelName);

            ArrayNode messagesNode = objectMapper.createArrayNode();
            for (Message msg : messages) {
                ObjectNode msgNode = objectMapper.createObjectNode();
                msgNode.put("role", msg.role());
                msgNode.put("content", msg.content());
                messagesNode.add(msgNode);
            }
            requestBody.set("messages", messagesNode);

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);

            String fullUrl = url + "/chat/completions";
            ResponseEntity<String> response = restTemplate.exchange(fullUrl, HttpMethod.POST, entity, String.class);

            return parseResponse(response.getBody());
        } catch (Exception e) {
            return new LLMResponse(null, "Error: " + e.getMessage());
        }
    }

    // ─── Single-prompt chat (backward compatibility) ─────────────

    public LLMResponse chat(String prompt) {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("user", prompt));
        return chat(messages);
    }
}
