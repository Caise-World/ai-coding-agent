package com.aicoding.agent.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String message;
    private String path;
}
