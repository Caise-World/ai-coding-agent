package com.aicoding.agent.rag;

import com.aicoding.agent.rag.chunk.ParserRegistry;
import com.aicoding.agent.rag.indexing.IndexingService;
import com.aicoding.agent.rag.indexing.IndexingService.IndexResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RagIndexDiag {

    @Autowired private IndexingService indexingService;
    @Autowired private ParserRegistry parserRegistry;

    @Test
    void diagnoseIndex() {
        String projectPath = Paths.get(".").toAbsolutePath().toString();
        IndexResult r = indexingService.indexProject(Paths.get(projectPath));

        System.out.println("\n=== INDEX RESULT ===");
        System.out.println("filesScanned: " + r.filesScanned());
        System.out.println("chunksCreated: " + r.chunksCreated());

        // Now scan manually and check what's in the workspace
        var javaFiles = new java.util.ArrayList<java.nio.file.Path>();
        var otherFiles = new java.util.ArrayList<java.nio.file.Path>();
        try (var stream = java.nio.file.Files.walk(Paths.get(projectPath))) {
            stream.filter(java.nio.file.Files::isRegularFile).forEach(p -> {
                if (p.toString().endsWith(".java")) javaFiles.add(p);
                else otherFiles.add(p);
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        System.out.println("\n=== ACTUAL FILES ON DISK ===");
        System.out.println("Total .java files: " + javaFiles.size());
        System.out.println("Total other files: " + otherFiles.size());

        System.out.println("\n=== ROUTING TEST ===");
        var sampleJava = Paths.get("/tmp/Foo.java");
        System.out.println("findFor(/tmp/Foo.java) -> " + parserRegistry.findFor(sampleJava));
        System.out.println("findFor(/tmp/test.xml) -> " + parserRegistry.findFor(Paths.get("/tmp/test.xml")));
        System.out.println("findFor(/tmp/test.md) -> " + parserRegistry.findFor(Paths.get("/tmp/test.md")));
    }
}