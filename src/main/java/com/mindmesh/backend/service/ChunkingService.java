package com.mindmesh.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mindmesh.backend.model.Document;
import com.mindmesh.backend.model.DocumentChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Serviço para dividir textos em chunks e gerar embeddings.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkingService {

    private static final int MIN_CHUNK_SIZE = 200;
    private static final int TARGET_CHUNK_SIZE = 800;
    private static final int MAX_CHUNK_SIZE = 1000;

    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");
    private static final Pattern MULTIPLE_NEWLINES = Pattern.compile("\\n{3,}");

    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Divide o conteúdo em chunks e gera embeddings.
     * Retorna lista de DocumentChunk associados ao Document.
     */
    public List<DocumentChunk> chunkAndEmbed(Document document, String content) {
        if (content == null || content.isBlank()) {
            log.warn("Conteúdo vazio para chunking");
            return List.of();
        }

        String normalizedContent = normalizeText(content);
        List<String> rawChunks = splitIntoChunks(normalizedContent);
        List<DocumentChunk> documentChunks = new ArrayList<>();

        for (int i = 0; i < rawChunks.size(); i++) {
            String chunkText = rawChunks.get(i);

            float[] embedding = embeddingService.embed(chunkText);
            JsonNode metadata = createMetadata(i);

            DocumentChunk chunk = DocumentChunk.builder()
                    .id(UUID.randomUUID())
                    .document(document)
                    .documentId(document.getId())
                    .content(chunkText)
                    .embedding(embedding)
                    .metadata(metadata)
                    .chunkIndex(i)
                    .tokenCount(estimateTokenCount(chunkText))
                    .build();

            documentChunks.add(chunk);
        }

        log.info("Chunking concluído: {} chunks para documento {}", documentChunks.size(), document.getId());
        return documentChunks;
    }

    private String normalizeText(String text) {
        String normalized = text.trim();
        normalized = MULTIPLE_SPACES.matcher(normalized).replaceAll(" ");
        normalized = MULTIPLE_NEWLINES.matcher(normalized).replaceAll("\n\n");
        return normalized;
    }

    private List<String> splitIntoChunks(String content) {
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = content.split("\\n\\n");
        StringBuilder currentChunk = new StringBuilder();

        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (paragraph.isEmpty())
                continue;

            if (currentChunk.length() + paragraph.length() > MAX_CHUNK_SIZE && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder();
            }

            if (currentChunk.length() > 0) {
                currentChunk.append("\n\n");
            }
            currentChunk.append(paragraph);

            if (currentChunk.length() >= TARGET_CHUNK_SIZE) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder();
            }
        }

        if (currentChunk.length() > 0) {
            String lastChunk = currentChunk.toString().trim();

            if (lastChunk.length() < MIN_CHUNK_SIZE && !chunks.isEmpty()) {
                String previousChunk = chunks.remove(chunks.size() - 1);
                lastChunk = previousChunk + "\n\n" + lastChunk;
            }

            if (lastChunk.length() > MAX_CHUNK_SIZE) {
                chunks.addAll(sliceLargeChunk(lastChunk));
            } else {
                chunks.add(lastChunk);
            }
        }

        List<String> finalChunks = new ArrayList<>();
        for (String chunk : chunks) {
            if (chunk.length() > MAX_CHUNK_SIZE) {
                finalChunks.addAll(sliceLargeChunk(chunk));
            } else {
                finalChunks.add(chunk);
            }
        }

        return finalChunks;
    }

    private List<String> sliceLargeChunk(String chunk) {
        List<String> slices = new ArrayList<>();

        while (chunk.length() > MAX_CHUNK_SIZE) {
            int cutPoint = findCutPoint(chunk, TARGET_CHUNK_SIZE);
            slices.add(chunk.substring(0, cutPoint).trim());
            chunk = chunk.substring(cutPoint).trim();
        }

        if (!chunk.isEmpty()) {
            slices.add(chunk);
        }

        return slices;
    }

    private int findCutPoint(String text, int targetPosition) {
        if (targetPosition >= text.length()) {
            return text.length();
        }

        for (int i = targetPosition; i > targetPosition / 2; i--) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?' || c == '\n') {
                return i + 1;
            }
        }

        for (int i = targetPosition; i > targetPosition / 2; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i + 1;
            }
        }

        return targetPosition;
    }

    private JsonNode createMetadata(int chunkIndex) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("chunk_index", chunkIndex);
        return metadata;
    }

    private int estimateTokenCount(String text) {
        int wordCount = text.split("\\s+").length;
        return (int) Math.ceil(wordCount * 1.3);
    }
}
