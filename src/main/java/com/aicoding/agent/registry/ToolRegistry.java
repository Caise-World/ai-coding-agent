package com.aicoding.agent.registry;

import com.aicoding.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ToolRegistry {

    private final Map<String, Tool> tools;

    public ToolRegistry() {
        this.tools = new ConcurrentHashMap<>();
    }

    public void register(Tool tool) {
        tools.put(tool.name(), tool);
    }

    public Tool get(String name) {
        return tools.get(name);
    }

    public List<Tool> getAll() {
        return new ArrayList<>(tools.values());
    }

    public List<String> getToolNames() {
        return new ArrayList<>(tools.keySet());
    }

    public boolean has(String name) {
        return tools.containsKey(name);
    }

    public int size() {
        return tools.size();
    }

    public String getToolsDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("Available tools:\n");
        for (Tool tool : tools.values()) {
            sb.append("- ").append(tool.name()).append(": ").append(tool.description()).append("\n");
        }
        return sb.toString();
    }

    public String getToolsJsonForLLM() {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        boolean first = true;
        for (Tool tool : tools.values()) {
            if (!first) sb.append(",\n");
            first = false;
            sb.append("  {\n");
            sb.append("    \"name\": \"").append(tool.name()).append("\",\n");
            sb.append("    \"description\": \"").append(tool.description()).append("\"\n");
            sb.append("  }");
        }
        sb.append("\n]");
        return sb.toString();
    }
}
