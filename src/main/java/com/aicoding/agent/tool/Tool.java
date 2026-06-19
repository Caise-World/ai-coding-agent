package com.aicoding.agent.tool;

import java.util.Map;

public interface Tool {
    String name();

    String description();

    Map<String, Object> getSchema();

    ToolResult execute(ToolContext context);

    default String execute(String input) {
        return execute(new ToolContext(input)).getOutput();
    }

    class ToolContext {
        private String input;
        private Map<String, Object> metadata;

        public ToolContext(String input) {
            this.input = input;
        }

        public String getInput() { return input; }
        public void setInput(String input) { this.input = input; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }

    class ToolResult {
        private String output;
        private boolean success;
        private String error;

        public ToolResult() {}

        public static ToolResult success(String output) {
            ToolResult result = new ToolResult();
            result.output = output;
            result.success = true;
            return result;
        }

        public static ToolResult error(String error) {
            ToolResult result = new ToolResult();
            result.error = error;
            result.success = false;
            return result;
        }

        public String getOutput() { return output; }
        public void setOutput(String output) { this.output = output; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }
}
