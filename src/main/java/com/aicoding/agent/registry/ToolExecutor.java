package com.aicoding.agent.registry;

import com.aicoding.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class ToolExecutor {

    private final ToolRegistry toolRegistry;

    public ToolExecutor(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public ToolExecutionResult execute(String toolName, String input) {
        Tool tool = toolRegistry.get(toolName);
        if (tool == null) {
            return ToolExecutionResult.error("Tool not found: " + toolName);
        }

        try {
            Tool.ToolContext context = new Tool.ToolContext(input);
            Tool.ToolResult result = tool.execute(context);

            if (result.isSuccess()) {
                return ToolExecutionResult.success(toolName, result.getOutput());
            } else {
                return ToolExecutionResult.error(result.getError());
            }
        } catch (Exception e) {
            return ToolExecutionResult.error("Execution error: " + e.getMessage());
        }
    }

    public boolean hasTool(String toolName) {
        return toolRegistry.has(toolName);
    }

    public String getAvailableTools() {
        return toolRegistry.getToolsDescription();
    }

    public record ToolExecutionResult(String toolName, String output, boolean success, String error) {
        public static ToolExecutionResult success(String toolName, String output) {
            return new ToolExecutionResult(toolName, output, true, null);
        }

        public static ToolExecutionResult error(String error) {
            return new ToolExecutionResult(null, null, false, error);
        }
    }
}
