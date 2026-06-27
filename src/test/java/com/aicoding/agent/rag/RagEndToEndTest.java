package com.aicoding.agent.rag;

import com.aicoding.agent.rag.workspace.WorkspaceService;
import com.aicoding.agent.rag.workspace.WorkspaceService.WorkspaceState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RagEndToEndTest {

    @Autowired private WorkspaceService workspaceService;
    @Autowired private RagService ragService;

    @Test
    void openWorkspaceAndRetrieveContext() {
        String projectPath = Paths.get(".").toAbsolutePath().toString();
        WorkspaceState state = workspaceService.openWorkspace(projectPath);

        assertTrue(state.isOpen());
        assertTrue(state.chunkCount() > 0, "Should index some chunks");

        String context = ragService.retrieveContext("self-healing reflection retry mechanism");
        assertFalse(context.isBlank(), "Should retrieve non-empty context");

        System.out.println("Indexed " + state.chunkCount() + " chunks");
        System.out.println("Retrieved context:\n" + context);

        assertTrue(context.contains("Reflection") || context.contains("reflection")
                        || context.contains("REPAIR") || context.contains("selfHealing")
                        || context.contains("executeWithSelfHealing"),
                "Context should mention self-healing related terms");
    }

    @Test
    void reopenWorkspaceUsesCache() {
        File cacheDir = new File(".rag-cache");
        if (cacheDir.exists()) {
            for (File f : cacheDir.listFiles()) {
                if (f.isDirectory()) {
                    for (File c : f.listFiles()) c.delete();
                }
                f.delete();
            }
        }

        String projectPath = Paths.get(".").toAbsolutePath().toString();
        WorkspaceState first = workspaceService.openWorkspace(projectPath);
        assertFalse(first.cacheHit(), "First open should not hit cache");
        assertTrue(first.chunkCount() > 0, "First open should have indexed chunks");

        WorkspaceState second = workspaceService.openWorkspace(projectPath);
        assertTrue(second.cacheHit(), "Second open should hit cache");
        assertEquals(first.chunkCount(), second.chunkCount(), "Cached and fresh should have same chunk count");
    }
}