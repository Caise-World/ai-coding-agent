package com.aicoding.agent.rag;

import com.aicoding.agent.rag.embedding.OllamaEmbedder;
import com.aicoding.agent.rag.store.ScoredChunk;
import com.aicoding.agent.rag.store.VectorStore;
import com.aicoding.agent.rag.workspace.WorkspaceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagService {

    private final VectorStore vectorStore;
    private final OllamaEmbedder embedder;
    private final WorkspaceService workspaceService;
    private final int topK;
    private final double minScore;

    public RagService(VectorStore vectorStore, OllamaEmbedder embedder, WorkspaceService workspaceService,
                      @Value("${rag.top-k}") int topK,
                      @Value("${rag.similarity-threshold}") double minScore) {
        this.vectorStore = vectorStore;
        this.embedder = embedder;
        this.workspaceService = workspaceService;
        this.topK = topK;
        this.minScore = minScore;
    }

    public String retrieveContext(String question) {
        if (!workspaceService.current().isOpen()) {
            return "";
        }
        if (vectorStore.size() == 0) {
            return "";
        }

        try {
            float[] qVec = embedder.embed(question);
            List<ScoredChunk> hits = vectorStore.topK(qVec, topK, minScore);
            return format(hits);
        } catch (Exception e) {
            return "[RAG error: " + e.getMessage() + "]";
        }
    }

    private String format(List<ScoredChunk> hits) {
        if (hits.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int idx = 1;
        for (ScoredChunk hit : hits) {
            sb.append("[").append(idx++).append("] ")
              .append(hit.chunk().location())
              .append(" (score=").append(String.format("%.3f", hit.score())).append(")\n")
              .append(hit.chunk().content())
              .append("\n\n");
        }
        return sb.toString().trim();
    }
}