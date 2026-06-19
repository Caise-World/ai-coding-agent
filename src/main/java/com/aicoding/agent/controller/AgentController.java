package com.aicoding.agent.controller;

import com.aicoding.agent.dto.ChatRequest;
import com.aicoding.agent.dto.ChatResponse;
import com.aicoding.agent.service.V4AgentService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final V4AgentService v4AgentService;

    public AgentController(V4AgentService v4AgentService) {
        this.v4AgentService = v4AgentService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return v4AgentService.chat(request);
    }
}
