package com.aicoding.agent.controller;

import com.aicoding.agent.dto.AgentEvent;
import com.aicoding.agent.dto.ChatRequest;
import com.aicoding.agent.memory.MemoryService;
import com.aicoding.agent.service.*;
import com.aicoding.agent.tool.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/agent")
public class StreamingAgentController {

    private final TaskPlannerService taskPlannerService;
    private final FailureAnalysisService failureAnalysisService;
    private final MemoryService memoryService;
    private final LLMService llmService;
    private final ProjectScanTool projectScanTool;
    private final FileReadTool fileReadTool;
    private final FileWriteTool fileWriteTool;
    private final CommandExecuteTool commandExecuteTool;
    private final String defaultProjectPath;

    public StreamingAgentController(
            TaskPlannerService taskPlannerService,
            FailureAnalysisService failureAnalysisService,
            MemoryService memoryService,
            LLMService llmService,
            ProjectScanTool projectScanTool,
            FileReadTool fileReadTool,
            FileWriteTool fileWriteTool,
            CommandExecuteTool commandExecuteTool,
            @Value("${agent.project-path}") String defaultProjectPath) {
        this.taskPlannerService = taskPlannerService;
        this.failureAnalysisService = failureAnalysisService;
        this.memoryService = memoryService;
        this.llmService = llmService;
        this.projectScanTool = projectScanTool;
        this.fileReadTool = fileReadTool;
        this.fileWriteTool = fileWriteTool;
        this.commandExecuteTool = commandExecuteTool;
        this.defaultProjectPath = defaultProjectPath;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AgentEvent> stream(@RequestParam String message,
                                   @RequestParam(required = false) String path) {
        ChatRequest request = new ChatRequest();
        request.setMessage(message);
        request.setPath(path);

        StreamingAgentService streamingService = new StreamingAgentService(
                taskPlannerService,
                failureAnalysisService,
                memoryService,
                llmService,
                projectScanTool,
                fileReadTool,
                fileWriteTool,
                commandExecuteTool,
                defaultProjectPath
        );

        return streamingService.executeStream(request);
    }

    @PostMapping("/stream")
    public Flux<AgentEvent> streamPost(@RequestBody ChatRequest request) {
        StreamingAgentService streamingService = new StreamingAgentService(
                taskPlannerService,
                failureAnalysisService,
                memoryService,
                llmService,
                projectScanTool,
                fileReadTool,
                fileWriteTool,
                commandExecuteTool,
                defaultProjectPath
        );

        return streamingService.executeStream(request);
    }
}
