package com.hr.ai.service;

import com.hr.ai.config.KnowledgeProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentChunkService {

    private final KnowledgeProperties knowledgeProperties;

    public DocumentChunkService(KnowledgeProperties knowledgeProperties) {
        this.knowledgeProperties = knowledgeProperties;
    }

    public List<String> chunk(String content) {
        int maxSize = knowledgeProperties.getMaxChunkSize();
        if (content.length() <= maxSize) {
            return List.of(content);
        }

        List<String> chunks = new ArrayList<>();
        String[] paragraphs = content.split("\n\n+");
        StringBuilder current = new StringBuilder();

        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (trimmed.length() > maxSize) {
                flushChunk(chunks, current);
                chunks.addAll(splitLongParagraph(trimmed, maxSize));
                continue;
            }

            if (current.length() + trimmed.length() + 2 > maxSize) {
                flushChunk(chunks, current);
            }

            if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(trimmed);
        }

        flushChunk(chunks, current);
        return chunks.isEmpty() ? List.of(content) : chunks;
    }

    private List<String> splitLongParagraph(String paragraph, int maxSize) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < paragraph.length()) {
            int end = Math.min(start + maxSize, paragraph.length());
            parts.add(paragraph.substring(start, end).trim());
            start = end;
        }
        return parts;
    }

    private void flushChunk(List<String> chunks, StringBuilder current) {
        if (!current.isEmpty()) {
            chunks.add(current.toString().trim());
            current.setLength(0);
        }
    }
}
