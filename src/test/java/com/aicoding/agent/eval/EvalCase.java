package com.aicoding.agent.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EvalCase(
        @JsonProperty("input") String input,
        @JsonProperty("expectedTool") String expectedTool,
        @JsonProperty("expectedNeedsRag") Boolean expectedNeedsRag
) {
    public boolean matches(String actualTool) {
        return expectedTool.equalsIgnoreCase(actualTool);
    }

    public boolean matchesRag(boolean actualNeedsRag) {
        return expectedNeedsRag == null || expectedNeedsRag == actualNeedsRag;
    }
}
