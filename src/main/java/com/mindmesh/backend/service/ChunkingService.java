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
import java.util.Map;
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
        return chunkAndEmbed(document, content, null);
    }

    /**
     * Divide o conteúdo em chunks e gera embeddings com metadados do documento.
     * Retorna lista de DocumentChunk associados ao Document.
     */
    public List<DocumentChunk> chunkAndEmbed(Document document, String content, Map<String, Object> documentMetadata) {
        if (content == null || content.isBlank()) {
            log.warn("Conteúdo vazio para chunking");
            return List.of();
        }

        String normalizedContent = normalizeText(content);
        List<String> rawChunks = splitIntoChunks(normalizedContent);
        List<DocumentChunk> documentChunks = new ArrayList<>();

        int totalChunks = rawChunks.size();

        for (int i = 0; i < rawChunks.size(); i++) {
            String chunkText = rawChunks.get(i);
            int tokenCount = estimateTokenCount(chunkText);

            float[] embedding = embeddingService.embed(chunkText);
            JsonNode metadata = createEnrichedMetadata(i, totalChunks, tokenCount, documentMetadata);

            DocumentChunk chunk = DocumentChunk.builder()
                    .id(UUID.randomUUID())
                    .document(document)
                    .documentId(document.getId())
                    .content(chunkText)
                    .embedding(embedding)
                    .metadata(metadata)
                    .chunkIndex(i)
                    .tokenCount(tokenCount)
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

    private JsonNode createEnrichedMetadata(int chunkIndex, int totalChunks, int tokenCount,
            Map<String, Object> documentMetadata) {
        ObjectNode metadata = objectMapper.createObjectNode();

        // Chunk-specific metadata
        metadata.put("chunk_index", chunkIndex);
        metadata.put("chunk_token_count", tokenCount);
        metadata.put("total_chunks", totalChunks);

        // Merge document-level metadata if provided
        if (documentMetadata != null) {
            for (Map.Entry<String, Object> entry : documentMetadata.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if (value instanceof String) {
                    metadata.put(key, (String) value);
                } else if (value instanceof Integer) {
                    metadata.put(key, (Integer) value);
                } else if (value instanceof Long) {
                    metadata.put(key, (Long) value);
                } else if (value instanceof Double) {
                    metadata.put(key, (Double) value);
                } else if (value instanceof Boolean) {
                    metadata.put(key, (Boolean) value);
                } else if (value instanceof java.util.List) {
                    metadata.set(key, objectMapper.valueToTree(value));
                } else if (value instanceof java.util.Map) {
                    metadata.set(key, objectMapper.valueToTree(value));
                } else if (value != null) {
                    metadata.put(key, value.toString());
                }
            }
        }

        return metadata;
    }

    private int estimateTokenCount(String text) {
        int wordCount = text.split("\\s+").length;
        return (int) Math.ceil(wordCount * 1.3);
    }
}
