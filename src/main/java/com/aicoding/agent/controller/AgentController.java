package com.aicoding.agent.controller;

import com.aicoding.agent.dto.ChatRequest;
import com.aicoding.agent.dto.ChatResponse;
import com.aicoding.agent.service.V6EngineeringAgentService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final V6EngineeringAgentService v6AgentService;

    public AgentController(V6EngineeringAgentService v6AgentService) {
        this.v6AgentService = v6AgentService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return v6AgentService.chat(request);
    }
}
