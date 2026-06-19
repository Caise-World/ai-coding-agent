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
    private final ObjectMapper objectMapper;

    public LLMService(
            RestTemplate restTemplate,
            @Value("${llm.api-key}") String apiKey,
            @Value("${llm.model}") String model,
            @Value("${llm.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.objectMapper = new ObjectMapper();
    }

    public LLMResponse chat(String systemPrompt, String userMessage) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);

            ArrayNode messages = objectMapper.createArrayNode();

            ObjectNode systemMsg = objectMapper.createObjectNode();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            messages.add(systemMsg);

            ObjectNode userMsg = objectMapper.createObjectNode();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            messages.add(userMsg);

            requestBody.set("messages", messages);

            // 添加 tools 参数启用 function calling
            ArrayNode tools = objectMapper.createArrayNode();
            ObjectNode tool = objectMapper.createObjectNode();
            tool.put("type", "function");

            ObjectNode function = objectMapper.createObjectNode();
            function.put("name", "ProjectScanTool");
            function.put("description", "Scans a project directory and returns its structure. Input: absolute directory path.");

            ObjectNode properties = objectMapper.createObjectNode();
            ObjectNode inputProp = objectMapper.createObjectNode();
            inputProp.put("type", "string");
            inputProp.put("description", "absolute directory path");
            properties.set("input", inputProp);
            function.set("parameters", properties);

            tool.set("function", function);
            tools.add(tool);

            // 添加 FileReadTool
            ObjectNode tool2 = objectMapper.createObjectNode();
            tool2.put("type", "function");

            ObjectNode function2 = objectMapper.createObjectNode();
            function2.put("name", "FileReadTool");
            function2.put("description", "Reads the content of a file. Input: absolute file path.");

            ObjectNode properties2 = objectMapper.createObjectNode();
            ObjectNode inputProp2 = objectMapper.createObjectNode();
            inputProp2.put("type", "string");
            inputProp2.put("description", "absolute file path");
            properties2.set("input", inputProp2);
            function2.set("parameters", properties2);

            tool2.set("function", function2);
            tools.add(tool2);

            requestBody.set("tools", tools);

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);

            String url = baseUrl + "/chat/completions";
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            return parseResponse(response.getBody());
        } catch (Exception e) {
            return new LLMResponse(null, null, "Error: " + e.getMessage());
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

            JsonNode toolCalls = message.get("tool_calls");
            if (toolCalls != null && toolCalls.isArray() && toolCalls.size() > 0) {
                JsonNode toolCall = toolCalls.get(0);
                String toolName = toolCall.get("function").get("name").asText();
                String arguments = toolCall.get("function").get("arguments").asText();
                return new LLMResponse(content, new ToolCall(toolName, arguments), null);
            }

            return new LLMResponse(content, null, null);
        }

        return new LLMResponse(null, null, "No response from LLM");
    }

    public record LLMResponse(String content, ToolCall toolCall, String error) {}
    public record ToolCall(String name, String arguments) {}
}
