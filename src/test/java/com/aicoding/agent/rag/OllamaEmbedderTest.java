package com.aicoding.agent.rag;

import com.aicoding.agent.rag.embedding.OllamaEmbedder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OllamaEmbedderTest {

    @Autowired
    private OllamaEmbedder embedder;

    @Test
    void embedReturnsCorrectDimension() {
        float[] vec = embedder.embed("hello world");
        assertEquals(768, vec.length, "nomic-embed-text produces 768-dim vectors");
    }

    @Test
    void similarTextsHaveHighCosineSimilarity() {
        float[] a = embedder.embed("how does self-healing work");
        float[] b = embedder.embed("self-healing mechanism in the agent");
        float[] c = embedder.embed("how to bake chocolate cake");

        double simAB = cosine(a, b);
        double simAC = cosine(a, c);
        assertTrue(simAB > simAC, "Related texts should be more similar than unrelated (got " + simAB + " vs " + simAC + ")");
    }

    private double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}