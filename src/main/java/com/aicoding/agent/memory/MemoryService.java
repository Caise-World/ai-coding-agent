package com.aicoding.agent.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MemoryService {

    private static final String MEMORY_DIR = "memory";
    private static final String SHORT_TERM_DIR = MEMORY_DIR + "/short-term";
    private static final String LONG_TERM_FILE = MEMORY_DIR + "/long-term/memory.json";

    private final ObjectMapper objectMapper;
    private final Map<String, Map<String, Object>> shortTermCache;
    private Map<String, Object> longTermCache;

    public MemoryService() {
        this.objectMapper = new ObjectMapper();
        this.shortTermCache = new ConcurrentHashMap<>();
        this.longTermCache = new ConcurrentHashMap<>();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(SHORT_TERM_DIR));
            Files.createDirectories(Paths.get(MEMORY_DIR + "/long-term"));

            File longTermFile = new File(LONG_TERM_FILE);
            if (longTermFile.exists()) {
                JsonNode node = objectMapper.readTree(longTermFile);
                if (node.isObject()) {
                    node.fields().forEachRemaining(field -> {
                        longTermCache.put(field.getKey(), field.getValue().asText());
                    });
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to initialize memory: " + e.getMessage());
        }
    }

    // Short-term memory operations
    public void saveShortTerm(String sessionId, String key, Object value) {
        shortTermCache.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>()).put(key, value);
        saveShortTermToFile(sessionId);
    }

    public Object getShortTerm(String sessionId, String key) {
        Map<String, Object> sessionMemory = shortTermCache.get(sessionId);
        if (sessionMemory == null) {
            sessionMemory = loadShortTermFromFile(sessionId);
            if (sessionMemory != null) {
                shortTermCache.put(sessionId, sessionMemory);
            }
        }
        return sessionMemory != null ? sessionMemory.get(key) : null;
    }

    public Map<String, Object> getAllShortTerm(String sessionId) {
        return shortTermCache.getOrDefault(sessionId, new HashMap<>());
    }

    public void clearShortTerm(String sessionId) {
        shortTermCache.remove(sessionId);
        deleteShortTermFile(sessionId);
    }

    // Long-term memory operations
    public void saveLongTerm(String key, Object value) {
        longTermCache.put(key, value);
        saveLongTermToFile();
    }

    public Object getLongTerm(String key) {
        return longTermCache.get(key);
    }

    public Map<String, Object> getAllLongTerm() {
        return new HashMap<>(longTermCache);
    }

    public String searchLongTerm(String query) {
        StringBuilder results = new StringBuilder();
        String lowerQuery = query.toLowerCase();

        for (Map.Entry<String, Object> entry : longTermCache.entrySet()) {
            String key = entry.getKey().toLowerCase();
            String value = entry.getValue().toString().toLowerCase();

            if (key.contains(lowerQuery) || value.contains(lowerQuery)) {
                results.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }

        return results.length() > 0 ? results.toString() : "No relevant memory found.";
    }

    // Memory context for prompts
    public String getMemoryContext() {
        if (longTermCache.isEmpty()) {
            return "No previous experience.";
        }

        StringBuilder context = new StringBuilder();
        context.append("Previous experiences:\n");

        int count = 0;
        for (Map.Entry<String, Object> entry : longTermCache.entrySet()) {
            if (count >= 5) {
                context.append("- ... and ").append(longTermCache.size() - 5).append(" more\n");
                break;
            }
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            if (value.length() > 100) {
                value = value.substring(0, 100) + "...";
            }
            context.append("- ").append(entry.getKey()).append(": ").append(value).append("\n");
            count++;
        }

        return context.toString();
    }

    // Private helpers
    private void saveShortTermToFile(String sessionId) {
        try {
            Map<String, Object> data = shortTermCache.get(sessionId);
            if (data != null) {
                ObjectNode node = objectMapper.createObjectNode();
                data.forEach((k, v) -> node.put(k, v.toString()));
                objectMapper.writeValue(new File(SHORT_TERM_DIR + "/" + sessionId + ".json"), node);
            }
        } catch (Exception e) {
            System.err.println("Failed to save short-term memory: " + e.getMessage());
        }
    }

    private Map<String, Object> loadShortTermFromFile(String sessionId) {
        try {
            File file = new File(SHORT_TERM_DIR + "/" + sessionId + ".json");
            if (file.exists()) {
                JsonNode node = objectMapper.readTree(file);
                Map<String, Object> data = new HashMap<>();
                node.fields().forEachRemaining(field -> data.put(field.getKey(), field.getValue().asText()));
                return data;
            }
        } catch (Exception e) {
            System.err.println("Failed to load short-term memory: " + e.getMessage());
        }
        return null;
    }

    private void deleteShortTermFile(String sessionId) {
        try {
            Files.deleteIfExists(Paths.get(SHORT_TERM_DIR + "/" + sessionId + ".json"));
        } catch (Exception e) {
            System.err.println("Failed to delete short-term memory file: " + e.getMessage());
        }
    }

    private void saveLongTermToFile() {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            longTermCache.forEach((k, v) -> node.put(k, v.toString()));
            objectMapper.writeValue(new File(LONG_TERM_FILE), node);
        } catch (Exception e) {
            System.err.println("Failed to save long-term memory: " + e.getMessage());
        }
    }

    // Save experience from agent execution
    public void saveExperience(String problemType, String solution, String context) {
        String key = problemType + ": " + context.substring(0, Math.min(50, context.length()));
        saveLongTerm(key, solution);
    }

    // Save failure for future reference
    public void saveFailure(String failedTask, String reason, String fixStrategy) {
        String key = "FAILURE: " + failedTask;
        String value = "Reason: " + reason + " | Fix: " + fixStrategy;
        saveLongTerm(key, value);
    }
}
