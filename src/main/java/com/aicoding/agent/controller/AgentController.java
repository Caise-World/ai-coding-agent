package com.aicoding.agent.controller;

import com.aicoding.agent.dto.ChatRequest;
import com.aicoding.agent.dto.ChatResponse;
import com.aicoding.agent.service.ReActAgentService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final ReActAgentService reactAgentService;

    public AgentController(ReActAgentService reactAgentService) {
        this.reactAgentService = reactAgentService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return reactAgentService.chat(request);
    }
}
