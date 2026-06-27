package com.aicoding.agent.controller;

import com.aicoding.agent.dto.WorkspaceRequest;
import com.aicoding.agent.dto.WorkspaceResponse;
import com.aicoding.agent.rag.workspace.WorkspaceService;
import com.aicoding.agent.rag.workspace.WorkspaceService.WorkspaceState;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @PostMapping("/open")
    public WorkspaceResponse open(@RequestBody WorkspaceRequest request) {
        if (request.getPath() == null || request.getPath().isBlank()) {
            return WorkspaceResponse.error("path is required");
        }
        try {
            WorkspaceState state = workspaceService.openWorkspace(request.getPath());
            WorkspaceResponse r = new WorkspaceResponse();
            r.setPath(state.path());
            r.setChunksCreated(state.chunkCount());
            r.setIndexTimeMs(state.indexTimeMs());
            r.setCacheHit(state.cacheHit());
            return r;
        } catch (IllegalArgumentException e) {
            return WorkspaceResponse.error(e.getMessage());
        }
    }

    @GetMapping("/current")
    public WorkspaceResponse current() {
        WorkspaceState state = workspaceService.current();
        if (!state.isOpen()) {
            return WorkspaceResponse.error("No workspace open");
        }
        WorkspaceResponse r = new WorkspaceResponse();
        r.setPath(state.path());
        r.setChunksCreated(state.chunkCount());
        return r;
    }
}