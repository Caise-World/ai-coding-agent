package com.aicoding.agent.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ChatRequest {
    private String message;
    private String path;
    private List<HistoryMessage> history;

    public List<HistoryMessage> getEffectiveHistory() {
        return history != null ? history : new ArrayList<>();
    }

    public record HistoryMessage(String role, String content) {}
}
