package com.aicoding.agent.eval;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EvalCase(
        @JsonProperty("input") String input,
        @JsonProperty("expectedTool") String expectedTool
) {
    public boolean matches(String actualTool) {
        return expectedTool.equalsIgnoreCase(actualTool);
    }
}
