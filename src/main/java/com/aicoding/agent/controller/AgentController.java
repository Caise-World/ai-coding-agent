package com.aicoding.agent.controller;

import com.aicoding.agent.dto.ChatRequest;
import com.aicoding.agent.dto.ChatResponse;
import com.aicoding.agent.service.V5AutonomousAgentService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final V5AutonomousAgentService v5AgentService;

    public AgentController(V5AutonomousAgentService v5AgentService) {
        this.v5AgentService = v5AgentService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return v5AgentService.chat(request);
    }
}
