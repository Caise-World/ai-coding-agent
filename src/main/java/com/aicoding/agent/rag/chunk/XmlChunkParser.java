package com.aicoding.agent.rag.chunk;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class XmlChunkParser implements ChunkParser {

    private static final int MAX_CHARS_PER_CHUNK = 8000;

    @Override
    public boolean supports(Path file) {
        return file.toString().endsWith(".xml");
    }

    @Override
    public List<Chunk> parse(Path file) throws Exception {
        String text = Files.readString(file);
        String path = file.toString();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
            doc.getDocumentElement().normalize();

            List<Chunk> chunks = new ArrayList<>();
            collectElements(doc.getDocumentElement(), path, chunks);
            if (chunks.isEmpty()) {
                chunks.add(new Chunk(path, 1, countLines(text), "XML", "document", text));
            }
            return chunks;
        } catch (Exception e) {
            return List.of(new Chunk(path, 1, countLines(text), "XML", "raw", truncate(text)));
        }
    }

    private void collectElements(Element root, String path, List<Chunk> chunks) {
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) node;
                String content = truncate(el.toString());
                chunks.add(new Chunk(
                        path,
                        -1, -1,
                        "XML",
                        el.getTagName(),
                        content
                ));
            }
        }
    }

    private int countLines(String text) {
        return (int) text.lines().count();
    }

    private String truncate(String s) {
        return s.length() <= MAX_CHARS_PER_CHUNK ? s : s.substring(0, MAX_CHARS_PER_CHUNK);
    }
}