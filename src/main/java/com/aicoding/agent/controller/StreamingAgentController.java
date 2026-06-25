package com.aicoding.agent.controller;

import com.aicoding.agent.dto.AgentEvent;
import com.aicoding.agent.dto.ChatRequest;
import com.aicoding.agent.memory.MemoryService;
import com.aicoding.agent.rag.RagService;
import com.aicoding.agent.rag.routing.CodeQuestionDetector;
import com.aicoding.agent.rag.workspace.WorkspaceService;
import com.aicoding.agent.registry.ToolExecutor;
import com.aicoding.agent.registry.ToolRegistry;
import com.aicoding.agent.registry.ToolSelector;
import com.aicoding.agent.service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/agent")
public class StreamingAgentController {

    private final ToolRegistry toolRegistry;
    private final ToolSelector toolSelector;
    private final ToolExecutor toolExecutor;
    private final MemoryService memoryService;
    private final LLMService llmService;
    private final RagService ragService;
    private final CodeQuestionDetector codeQuestionDetector;
    private final WorkspaceService workspaceService;
    private final String defaultProjectPath;

    public StreamingAgentController(
            ToolRegistry toolRegistry,
            ToolSelector toolSelector,
            ToolExecutor toolExecutor,
            MemoryService memoryService,
            LLMService llmService,
            RagService ragService,
            CodeQuestionDetector codeQuestionDetector,
            WorkspaceService workspaceService,
            @Value("${agent.project-path}") String defaultProjectPath) {
        this.toolRegistry = toolRegistry;
        this.toolSelector = toolSelector;
        this.toolExecutor = toolExecutor;
        this.memoryService = memoryService;
        this.llmService = llmService;
        this.ragService = ragService;
        this.codeQuestionDetector = codeQuestionDetector;
        this.workspaceService = workspaceService;
        this.defaultProjectPath = defaultProjectPath;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AgentEvent> stream(@RequestParam String message,
                                   @RequestParam(required = false) String path) {
        ChatRequest request = new ChatRequest();
        request.setMessage(message);
        request.setPath(path);

        PluginBasedStreamingAgentService streamingService = new PluginBasedStreamingAgentService(
                toolRegistry,
                toolSelector,
                toolExecutor,
                memoryService,
                llmService,
                ragService,
                codeQuestionDetector,
                workspaceService,
                defaultProjectPath
        );

        return streamingService.executeStream(request);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AgentEvent> streamPost(@RequestBody ChatRequest request) {
        PluginBasedStreamingAgentService streamingService = new PluginBasedStreamingAgentService(
                toolRegistry,
                toolSelector,
                toolExecutor,
                memoryService,
                llmService,
                ragService,
                codeQuestionDetector,
                workspaceService,
                defaultProjectPath
        );

        return streamingService.executeStream(request);
    }
}
