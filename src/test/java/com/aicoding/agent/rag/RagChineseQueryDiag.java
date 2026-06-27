package com.aicoding.agent.rag;

import com.aicoding.agent.rag.workspace.WorkspaceService;
import com.aicoding.agent.rag.workspace.WorkspaceService.WorkspaceState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Paths;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RagChineseQueryDiag {

    @Autowired private WorkspaceService workspaceService;
    @Autowired private RagService ragService;

    @Test
    void diagnose() {
        String projectPath = Paths.get(".").toAbsolutePath().toString();
        WorkspaceState state = workspaceService.openWorkspace(projectPath);

        System.out.println("\n=== STATE ===");
        System.out.println("chunkCount: " + state.chunkCount());
        System.out.println("cacheHit: " + state.cacheHit());

        String[] queries = {
                "PluginBasedStreamingAgentService 是怎么实现自愈的",
                "self-healing reflection retry mechanism",
                "MemoryService 怎么存长期记忆",
                "执行 mvn clean compile",
                "你好，你是谁"
        };

        for (String q : queries) {
            String ctx = ragService.retrieveContext(q);
            System.out.println("\n--- Q: " + q);
            System.out.println("ctx length: " + (ctx == null ? -1 : ctx.length()));
            System.out.println("ctx isBlank: " + (ctx == null || ctx.isBlank()));
            if (ctx != null && !ctx.isBlank()) {
                System.out.println("ctx first 200 chars:\n" + ctx.substring(0, Math.min(200, ctx.length())));
            }
        }
    }
}