package com.aicoding.agent.rag.indexing;

import com.aicoding.agent.rag.chunk.Chunk;
import com.aicoding.agent.rag.chunk.ChunkParser;
import com.aicoding.agent.rag.chunk.ParserRegistry;
import com.aicoding.agent.rag.embedding.OllamaEmbedder;
import com.aicoding.agent.rag.store.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;

@Service
public class IndexingService {

    private static final Logger log = LoggerFactory.getLogger(IndexingService.class);
    private static final int MAX_DEPTH = 12;
    private static final int MAX_FILES = 500;

    private final ParserRegistry parserRegistry;
    private final OllamaEmbedder embedder;
    private final VectorStore vectorStore;

    public IndexingService(ParserRegistry parserRegistry, OllamaEmbedder embedder, VectorStore vectorStore) {
        this.parserRegistry = parserRegistry;
        this.embedder = embedder;
        this.vectorStore = vectorStore;
    }

    public IndexResult indexProject(Path projectPath) {
        long start = System.currentTimeMillis();
        List<Path> files = scanFiles(projectPath);
        log.info("Scanned {} files from {}", files.size(), projectPath);

        List<Chunk> allChunks = new ArrayList<>();
        for (Path file : files) {
            ChunkParser parser = parserRegistry.findFor(file);
            if (parser == null) continue;
            try {
                List<Chunk> chunks = parser.parse(file);
                log.info("  {} → {} chunks", file, chunks.size());
                allChunks.addAll(chunks);
            } catch (Exception e) {
                log.warn("Failed to parse {}: {}", file, e.getMessage());
            }
        }
        log.info("Total chunks parsed: {}", allChunks.size());

        vectorStore.clear();
        int indexed = 0;
        for (Chunk chunk : allChunks) {
            try {
                float[] vec = embedder.embed(chunk.content());
                vectorStore.add(chunk, vec);
                indexed++;
            } catch (Exception e) {
                log.warn("Failed to embed {}: {}", chunk.location(), e.getMessage());
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("Indexed {} chunks from {} files in {}ms", indexed, files.size(), elapsed);
        return new IndexResult(projectPath.toString(), files.size(), indexed, elapsed);
    }

    private List<Path> scanFiles(Path root) {
        List<Path> files = new ArrayList<>();
        try {
            Path absoluteRoot = root.toAbsolutePath().normalize();
            Files.walkFileTree(absoluteRoot, new SimpleFileVisitor<>() {
                int depth = 0;
                int count = 0;

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (count >= MAX_FILES) return FileVisitResult.TERMINATE;
                    if (!dir.equals(absoluteRoot)) {
                        String name = dir.getFileName().toString();
                        if (name.startsWith(".") || name.equals("target") || name.equals("node_modules")
                                || name.equals("build") || name.equals("dist") || name.equals(".rag-cache")) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                    }
                    depth++;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    depth--;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (count >= MAX_FILES) return FileVisitResult.TERMINATE;
                    if (depth > MAX_DEPTH) return FileVisitResult.CONTINUE;
                    files.add(file);
                    count++;
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("Failed to scan {}: {}", root, e.getMessage());
        }
        return files;
    }

    public record IndexResult(String path, int filesScanned, int chunksCreated, long indexTimeMs) {}
}