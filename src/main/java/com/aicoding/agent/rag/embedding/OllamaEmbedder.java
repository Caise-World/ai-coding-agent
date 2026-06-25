package com.aicoding.agent.rag.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class OllamaEmbedder {

    private static final Logger log = LoggerFactory.getLogger(OllamaEmbedder.class);
    private static final int MAX_RETRIES = 3;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String host;
    private final String model;
    private final int dimension;

    public OllamaEmbedder(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${ollama.host}") String host,
            @Value("${ollama.embed-model}") String model,
            @Value("${ollama.embed-dimension}") int dimension) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.host = host;
        this.model = model;
        this.dimension = dimension;
    }

    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[dimension];
        }
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                float[] result = doEmbed(text);
                if (isFinite(result)) {
                    return result;
                }
                log.warn("Ollama returned non-finite embedding (attempt {}/{}) for text of length {}",
                        attempt, MAX_RETRIES, text.length());
                lastError = new RuntimeException("Non-finite values in embedding response");
            } catch (RuntimeException e) {
                log.warn("Ollama embed attempt {}/{} failed: {}", attempt, MAX_RETRIES, e.getMessage());
                lastError = e;
            }
            try {
                Thread.sleep(200L * attempt);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw lastError != null
                ? lastError
                : new RuntimeException("Failed to fetch finite embedding from Ollama at " + host);
    }

    private float[] doEmbed(String text) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("prompt", text);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    host + "/api/embeddings",
                    HttpMethod.POST,
                    entity,
                    String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode embeddingNode = root.get("embedding");
            if (embeddingNode == null || !embeddingNode.isArray()) {
                throw new RuntimeException("No 'embedding' in Ollama response: " + response.getBody());
            }

            float[] result = new float[embeddingNode.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = (float) embeddingNode.get(i).asDouble();
            }
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch embedding from Ollama at " + host, e);
        }
    }

    private static boolean isFinite(float[] v) {
        if (v == null || v.length == 0) return false;
        for (float x : v) {
            if (!Float.isFinite(x)) return false;
        }
        return true;
    }

    public int dimension() {
        return dimension;
    }
}