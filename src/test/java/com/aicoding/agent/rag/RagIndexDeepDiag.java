package com.aicoding.agent.rag;

import com.aicoding.agent.rag.chunk.ChunkParser;
import com.aicoding.agent.rag.chunk.ParserRegistry;
import com.aicoding.agent.rag.indexing.IndexingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RagIndexDeepDiag {

    @Autowired private IndexingService indexingService;
    @Autowired private ParserRegistry parserRegistry;

    @Test
    void diagnoseFilesAndParsers() throws Exception {
        Method m = IndexingService.class.getDeclaredMethod("scanFiles", Path.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Path> files = (List<Path>) m.invoke(indexingService, Paths.get(".").toAbsolutePath());

        System.out.println("\n=== IndexingService.scanFiles() returned " + files.size() + " files ===");

        Map<String, Long> byExt = files.stream()
                .collect(Collectors.groupingBy(
                        p -> {
                            String n = p.getFileName().toString();
                            int dot = n.lastIndexOf('.');
                            return dot < 0 ? "(no ext)" : n.substring(dot);
                        }, TreeMap::new, Collectors.counting()));
        byExt.forEach((ext, count) -> System.out.printf("  %-15s %d%n", ext, count));

        System.out.println("\n=== Routing check for .java files ===");
        List<Path> javaFiles = files.stream()
                .filter(p -> p.toString().endsWith(".java"))
                .toList();
        System.out.println("Total .java: " + javaFiles.size());
        int nullCount = 0, nonNullCount = 0;
        for (Path jf : javaFiles.subList(0, Math.min(10, javaFiles.size()))) {
            ChunkParser p = parserRegistry.findFor(jf);
            System.out.println("  " + jf.getFileName() + " -> " + (p == null ? "NULL" : p.getClass().getSimpleName()));
            if (p == null) nullCount++; else nonNullCount++;
        }
        System.out.println("  ...");
        System.out.println("Sample of 10: null=" + nullCount + " non-null=" + nonNullCount);
    }
}