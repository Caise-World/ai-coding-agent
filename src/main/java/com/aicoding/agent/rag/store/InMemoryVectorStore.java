package com.aicoding.agent.rag.store;

import com.aicoding.agent.rag.chunk.Chunk;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class InMemoryVectorStore implements VectorStore {

    private static final String META_FILE = "chunks.json";
    private static final String EMBED_FILE = "embeddings.bin";

    private final ObjectMapper objectMapper;
    private final List<Chunk> chunks = new CopyOnWriteArrayList<>();
    private final List<float[]> embeddings = new CopyOnWriteArrayList<>();

    public InMemoryVectorStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void add(Chunk chunk, float[] embedding) {
        chunks.add(chunk);
        embeddings.add(embedding);
    }

    @Override
    public List<ScoredChunk> topK(float[] queryVec, int k) {
        return topK(queryVec, k, Double.NEGATIVE_INFINITY);
    }

    @Override
    public List<ScoredChunk> topK(float[] queryVec, int k, double minScore) {
        List<ScoredChunk> scored = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            double score = cosine(queryVec, embeddings.get(i));
            if (score >= minScore) {
                scored.add(new ScoredChunk(chunks.get(i), score));
            }
        }
        scored.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        if (scored.size() > k) {
            return scored.subList(0, k);
        }
        return scored;
    }

    private double cosine(float[] a, float[] b) {
        if (a.length != b.length) return 0.0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return (na > 0 && nb > 0) ? dot / (Math.sqrt(na) * Math.sqrt(nb)) : 0.0;
    }

    @Override
    public int size() {
        return chunks.size();
    }

    @Override
    public void clear() {
        chunks.clear();
        embeddings.clear();
    }

    @Override
    public void save(Path cacheDir) throws IOException {
        Files.createDirectories(cacheDir);

        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode arr = root.putArray("chunks");
        for (Chunk c : chunks) {
            ObjectNode n = arr.addObject();
            n.put("path", c.path());
            n.put("startLine", c.startLine());
            n.put("endLine", c.endLine());
            n.put("kind", c.kind());
            n.put("symbol", c.symbol());
            n.put("content", c.content());
        }
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(cacheDir.resolve(META_FILE).toFile(), root);

        try (var out = new java.io.DataOutputStream(new java.io.BufferedOutputStream(Files.newOutputStream(cacheDir.resolve(EMBED_FILE))))) {
            for (float[] vec : embeddings) {
                for (float v : vec) {
                    out.writeFloat(v);
                }
            }
        }
    }

    @Override
    public void load(Path cacheDir) throws IOException {
        Path metaPath = cacheDir.resolve(META_FILE);
        Path embedPath = cacheDir.resolve(EMBED_FILE);
        if (!Files.exists(metaPath) || !Files.exists(embedPath)) {
            throw new IOException("Cache files not found in " + cacheDir);
        }

        ObjectNode root = (ObjectNode) objectMapper.readTree(metaPath.toFile());
        List<Chunk> loadedChunks = new ArrayList<>();
        for (var node : root.withArray("chunks")) {
            loadedChunks.add(new Chunk(
                    node.get("path").asText(),
                    node.get("startLine").asInt(),
                    node.get("endLine").asInt(),
                    node.get("kind").asText(),
                    node.get("symbol").asText(),
                    node.get("content").asText()
            ));
        }

        if (loadedChunks.isEmpty()) {
            clear();
            return;
        }

        byte[] bytes = Files.readAllBytes(embedPath);
        int totalFloats = bytes.length / 4;
        int floatsPerVec = totalFloats / loadedChunks.size();
        List<float[]> loadedVecs = new ArrayList<>();
        try (var in = new java.io.DataInputStream(new java.io.BufferedInputStream(new java.io.ByteArrayInputStream(bytes)))) {
            for (int i = 0; i < loadedChunks.size(); i++) {
                float[] vec = new float[floatsPerVec];
                for (int j = 0; j < floatsPerVec; j++) {
                    vec[j] = in.readFloat();
                }
                loadedVecs.add(vec);
            }
        }

        clear();
        chunks.addAll(loadedChunks);
        embeddings.addAll(loadedVecs);
    }
}