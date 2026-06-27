package com.aicoding.agent.rag.chunk;

import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MarkdownChunkParser implements ChunkParser {

    private static final Pattern HEADER_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
    private static final int MAX_CHARS_PER_CHUNK = 8000;

    @Override
    public boolean supports(Path file) {
        return file.toString().endsWith(".md");
    }

    @Override
    public List<Chunk> parse(Path file) throws Exception {
        String text = Files.readString(file);
        String path = file.toString();
        List<Chunk> chunks = new ArrayList<>();

        Matcher m = HEADER_PATTERN.matcher(text);
        List<int[]> headerPositions = new ArrayList<>();
        while (m.find()) {
            headerPositions.add(new int[]{m.start(), m.end(), m.group(2).trim().length()});
        }

        if (headerPositions.isEmpty()) {
            chunks.add(new Chunk(path, 1, (int) text.lines().count(), "MD", file.getFileName().toString(),
                    truncate(text)));
            return chunks;
        }

        if (headerPositions.get(0)[0] > 0) {
            String pre = text.substring(0, headerPositions.get(0)[0]).trim();
            if (!pre.isEmpty()) {
                chunks.add(new Chunk(path, 1, lineOf(text, headerPositions.get(0)[0]), "MD", "(preamble)", truncate(pre)));
            }
        }

        for (int i = 0; i < headerPositions.size(); i++) {
            int start = headerPositions.get(i)[0];
            int end = i + 1 < headerPositions.size() ? headerPositions.get(i + 1)[0] : text.length();
            String section = text.substring(start, end).trim();
            String title = extractHeader(text, headerPositions.get(i));
            chunks.add(new Chunk(
                    path,
                    lineOf(text, start),
                    lineOf(text, end - 1),
                    "MD",
                    title,
                    truncate(section)
            ));
        }

        return chunks;
    }

    private String extractHeader(String text, int[] pos) {
        String line = text.substring(pos[0], pos[1]);
        return line.replaceAll("^#+\\s*", "").trim();
    }

    private int lineOf(String text, int charPos) {
        int line = 1;
        for (int i = 0; i < charPos && i < text.length(); i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return line;
    }

    private String truncate(String s) {
        return s.length() <= MAX_CHARS_PER_CHUNK ? s : s.substring(0, MAX_CHARS_PER_CHUNK);
    }
}