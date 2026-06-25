package com.aicoding.agent.rag.chunk;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class JavaChunkParser implements ChunkParser {

    private static final int MAX_CHARS_PER_CHUNK = 8000;

    @PostConstruct
    public void configure() {
        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
    }

    @Override
    public boolean supports(Path file) {
        return file.toString().endsWith(".java");
    }

    @Override
    public List<Chunk> parse(Path file) throws Exception {
        // StaticJavaParser.getParserConfiguration() is thread-local and @PostConstruct
        // only configures the main thread. Use a local JavaParser instance with
        // JAVA_17 to support records/sealed types in worker threads.
        ParserConfiguration localConfig = new ParserConfiguration();
        localConfig.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        CompilationUnit cu = new com.github.javaparser.JavaParser(localConfig).parse(file).getResult()
                .orElseThrow(() -> new RuntimeException("Failed to parse: " + file));
        List<Chunk> chunks = new ArrayList<>();
        String path = file.toString();

        for (TypeDeclaration<?> typeDecl : cu.getTypes()) {
            if (typeDecl instanceof ClassOrInterfaceDeclaration) {
                ClassOrInterfaceDeclaration cls = (ClassOrInterfaceDeclaration) typeDecl;
                String content = truncate(typeDecl.toString());
                chunks.add(new Chunk(
                        path,
                        typeDecl.getBegin().get().line,
                        typeDecl.getEnd().get().line,
                        "CLASS",
                        cls.getNameAsString(),
                        content
                ));
            }
        }

        cu.findAll(MethodDeclaration.class).forEach(method -> {
            String content = truncate(method.toString());
            chunks.add(new Chunk(
                    path,
                    method.getBegin().get().line,
                    method.getEnd().get().line,
                    "METHOD",
                    method.getNameAsString(),
                    content
            ));
        });

        return chunks;
    }

    private String truncate(String s) {
        return s.length() <= MAX_CHARS_PER_CHUNK ? s : s.substring(0, MAX_CHARS_PER_CHUNK);
    }
}