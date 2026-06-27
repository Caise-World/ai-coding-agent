package com.aicoding.agent.rag;

import com.aicoding.agent.rag.chunk.Chunk;
import com.aicoding.agent.rag.chunk.JavaChunkParser;
import com.aicoding.agent.rag.chunk.MarkdownChunkParser;
import com.aicoding.agent.rag.chunk.ParserRegistry;
import com.aicoding.agent.rag.chunk.PropertiesChunkParser;
import com.aicoding.agent.rag.chunk.XmlChunkParser;
import com.aicoding.agent.rag.chunk.YamlChunkParser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ChunkParserTest {

    @Autowired private JavaChunkParser javaParser;
    @Autowired private XmlChunkParser xmlParser;
    @Autowired private YamlChunkParser yamlParser;
    @Autowired private MarkdownChunkParser mdParser;
    @Autowired private PropertiesChunkParser propsParser;
    @Autowired private ParserRegistry registry;

    @Test
    void javaParserExtractsClassAndMethodChunks() throws Exception {
        Path file = Paths.get("src/main/java/com/aicoding/agent/rag/chunk/JavaChunkParser.java");
        List<Chunk> chunks = javaParser.parse(file);

        assertTrue(chunks.stream().anyMatch(c -> "CLASS".equals(c.kind())
                        && "JavaChunkParser".equals(c.symbol())),
                "Should extract CLASS chunk for JavaChunkParser");
        assertTrue(chunks.stream().anyMatch(c -> "METHOD".equals(c.kind())
                        && "parse".equals(c.symbol())),
                "Should extract METHOD chunk for parse()");
        chunks.forEach(c -> assertTrue(c.startLine() > 0, "Line numbers should be set"));
    }

    @Test
    void xmlParserExtractsPomElements() throws Exception {
        Path file = Paths.get("pom.xml");
        List<Chunk> chunks = xmlParser.parse(file);
        assertFalse(chunks.isEmpty(), "Should extract at least one chunk from pom.xml");
    }

    @Test
    void yamlParserExtractsApplicationYaml() throws Exception {
        Path file = Paths.get("src/main/resources/application.yml");
        List<Chunk> chunks = yamlParser.parse(file);
        assertEquals(1, chunks.size());
        assertEquals("YAML", chunks.get(0).kind());
        assertTrue(chunks.get(0).content().contains("spring:"));
    }

    @Test
    void markdownParserExtractsHeaders() throws Exception {
        Path file = Paths.get("CLAUDE.md");
        if (!file.toFile().exists()) return;
        List<Chunk> chunks = mdParser.parse(file);
        assertTrue(chunks.size() > 1, "Should split README into multiple sections by headers");
        assertTrue(chunks.stream().anyMatch(c -> "MD".equals(c.kind())));
    }

    @Test
    void parserRegistryRoutesByExtension() {
        assertSame(javaParser, registry.findFor(Paths.get("Foo.java")));
        assertSame(xmlParser, registry.findFor(Paths.get("Foo.xml")));
        assertSame(yamlParser, registry.findFor(Paths.get("Foo.yml")));
        assertSame(mdParser, registry.findFor(Paths.get("Foo.md")));
        assertSame(propsParser, registry.findFor(Paths.get("Foo.properties")));
    }
}