package com.aicoding.agent.tool;

import com.aicoding.agent.sandbox.CommandRequest;
import com.aicoding.agent.sandbox.ExecutionResult;
import com.aicoding.agent.sandbox.SandboxExecutor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class CommandExecuteTool implements Tool {

    private final SandboxExecutor sandboxExecutor;

    public CommandExecuteTool(SandboxExecutor sandboxExecutor) {
        this.sandboxExecutor = sandboxExecutor;
    }

    @Override
    public String name() {
        return "CommandExecuteTool";
    }

    @Override
    public String description() {
        return "Executes shell commands in Docker sandbox. Input: absolute command to execute. Output: stdout + stderr + exit code.";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("name", "CommandExecuteTool");
        schema.put("description", "Executes shell commands in Docker sandbox with security restrictions");

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> inputProp = new HashMap<>();
        inputProp.put("type", "string");
        inputProp.put("description", "Command to execute (e.g., 'mvn compile', 'ls -la', 'echo hello')");
        properties.put("input", inputProp);

        schema.put("parameters", properties);
        return schema;
    }

    @Override
    public ToolResult execute(ToolContext context) {
        String input = context.getInput();
        CommandRequest request = new CommandRequest(input);

        ExecutionResult result = sandboxExecutor.execute(request);

        StringBuilder output = new StringBuilder();
        output.append("Executing in sandbox: ").append(input).append("\n");
        output.append("=".repeat(50)).append("\n");

        if (result.getError() != null) {
            output.append("ERROR: ").append(result.getError()).append("\n");
        } else {
            if (result.getStdout() != null && !result.getStdout().isBlank()) {
                output.append("STDOUT:\n").append(result.getStdout());
            }
            if (result.getStderr() != null && !result.getStderr().isBlank()) {
                output.append("STDERR:\n").append(result.getStderr());
            }
        }

        output.append("=".repeat(50)).append("\n");
        output.append("Exit code: ").append(result.getExitCode()).append("\n");
        output.append("Cost time: ").append(result.getCostTimeMs()).append("ms\n");

        return ToolResult.success(output.toString());
    }
}
