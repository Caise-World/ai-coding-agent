package com.aicoding.agent.rag.workspace;

import com.aicoding.agent.rag.indexing.IndexingService;
import com.aicoding.agent.rag.indexing.IndexingService.IndexResult;
import com.aicoding.agent.rag.store.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;

@Service
public class WorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceService.class);

    private final IndexingService indexingService;
    private final VectorStore vectorStore;
    private final Path cacheRoot;

    private volatile WorkspaceState currentState = WorkspaceState.closed();

    public WorkspaceService(IndexingService indexingService, VectorStore vectorStore,
                            @Value("${rag.cache-dir}") String cacheDir) {
        this.indexingService = indexingService;
        this.vectorStore = vectorStore;
        this.cacheRoot = Paths.get(cacheDir);
    }

    public synchronized WorkspaceState openWorkspace(String projectPath) {
        Path path = Paths.get(projectPath).toAbsolutePath();
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("Not a directory: " + projectPath);
        }

        String hash = sha1(path.toString());
        Path cacheDir = cacheRoot.resolve(hash);

        if (Files.exists(cacheDir.resolve("chunks.json")) && Files.exists(cacheDir.resolve("embeddings.bin"))) {
            log.info("Loading cached index from {}", cacheDir);
            try {
                vectorStore.load(cacheDir);
                WorkspaceState state = new WorkspaceState(path.toString(), vectorStore.size(), 0L, true);
                currentState = state;
                return state;
            } catch (IOException e) {
                log.warn("Cache load failed, rebuilding: {}", e.getMessage());
            }
        }

        log.info("Building fresh index for {}", path);
        IndexResult result = indexingService.indexProject(path);

        try {
            Files.createDirectories(cacheDir);
            vectorStore.save(cacheDir);
        } catch (IOException e) {
            log.warn("Failed to save cache: {}", e.getMessage());
        }

        WorkspaceState state = new WorkspaceState(path.toString(), result.chunksCreated(), result.indexTimeMs(), false);
        currentState = state;
        return state;
    }

    public WorkspaceState current() {
        return currentState;
    }

    private String sha1(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    public record WorkspaceState(String path, int chunkCount, long indexTimeMs, boolean cacheHit) {
        public static WorkspaceState closed() {
            return new WorkspaceState(null, 0, 0L, false);
        }
        public boolean isOpen() { return path != null; }
    }
}