package com.mindmesh.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mindmesh.backend.model.DocumentChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Serviço responsável por dividir textos em chunks e gerar embeddings.
 * Prepara DocumentChunks prontos para persistência.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkingService {

    private static final int MIN_CHUNK_SIZE = 200; // Mínimo de caracteres por chunk
    private static final int TARGET_CHUNK_SIZE = 800; // Tamanho ideal
    private static final int MAX_CHUNK_SIZE = 1000; // Máximo antes de fatiar

    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");
    private static final Pattern MULTIPLE_NEWLINES = Pattern.compile("\\n{3,}");

    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Divide o conteúdo em chunks, normaliza, gera embeddings e retorna lista de
     * DocumentChunk.
     *
     * @param documentId ID do documento pai
     * @param content    Texto completo a ser dividido
     * @return Lista de DocumentChunk prontos para persistência
     */
    public List<DocumentChunk> chunkAndEmbed(UUID documentId, String content) {
        if (content == null || content.isBlank()) {
            log.warn("Conteúdo vazio recebido para chunking");
            return List.of();
        }

        // 1. Normalizar texto
        String normalizedContent = normalizeText(content);

        // 2. Dividir em chunks
        List<String> rawChunks = splitIntoChunks(normalizedContent);

        // 3. Gerar DocumentChunks com embeddings
        List<DocumentChunk> documentChunks = new ArrayList<>();

        for (int i = 0; i < rawChunks.size(); i++) {
            String chunkText = rawChunks.get(i);

            // Gerar embedding
            float[] embedding = embeddingService.embed(chunkText);

            // Criar metadata
            JsonNode metadata = createMetadata(i);

            // Criar DocumentChunk
            DocumentChunk chunk = DocumentChunk.builder()
                    .id(UUID.randomUUID())
                    .documentId(documentId)
                    .content(chunkText)
                    .embedding(embedding)
                    .metadata(metadata)
                    .chunkIndex(i)
                    .tokenCount(estimateTokenCount(chunkText))
                    .build();

            documentChunks.add(chunk);
        }

        log.info("Chunking concluído: {} chunks gerados para documento {}",
                documentChunks.size(), documentId);

        return documentChunks;
    }

    /**
     * Normaliza o texto removendo espaços e linhas duplicadas.
     */
    private String normalizeText(String text) {
        String normalized = text.trim();
        normalized = MULTIPLE_SPACES.matcher(normalized).replaceAll(" ");
        normalized = MULTIPLE_NEWLINES.matcher(normalized).replaceAll("\n\n");
        return normalized;
    }

    /**
     * Divide o texto em chunks baseado em parágrafos.
     * Rejunta chunks pequenos e fatia chunks grandes.
     */
    private List<String> splitIntoChunks(String content) {
        List<String> chunks = new ArrayList<>();

        // Dividir por parágrafos (dupla quebra de linha)
        String[] paragraphs = content.split("\\n\\n");

        StringBuilder currentChunk = new StringBuilder();

        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (paragraph.isEmpty())
                continue;

            // Se adicionar este parágrafo ultrapassar o máximo, finalizar chunk atual
            if (currentChunk.length() + paragraph.length() > MAX_CHUNK_SIZE && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder();
            }

            // Adicionar parágrafo ao chunk atual
            if (currentChunk.length() > 0) {
                currentChunk.append("\n\n");
            }
            currentChunk.append(paragraph);

            // Se o chunk atual atingiu o tamanho ideal, finalizar
            if (currentChunk.length() >= TARGET_CHUNK_SIZE) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder();
            }
        }

        // Adicionar último chunk se houver conteúdo
        if (currentChunk.length() > 0) {
            String lastChunk = currentChunk.toString().trim();

            // Se último chunk for muito pequeno, tentar juntar com o anterior
            if (lastChunk.length() < MIN_CHUNK_SIZE && !chunks.isEmpty()) {
                String previousChunk = chunks.remove(chunks.size() - 1);
                lastChunk = previousChunk + "\n\n" + lastChunk;
            }

            // Se ainda for muito grande, fatiar
            if (lastChunk.length() > MAX_CHUNK_SIZE) {
                chunks.addAll(sliceLargeChunk(lastChunk));
            } else {
                chunks.add(lastChunk);
            }
        }

        // Processar chunks que ainda estão muito grandes
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

    /**
     * Fatia um chunk grande em partes menores, tentando cortar em pontos naturais.
     */
    private List<String> sliceLargeChunk(String chunk) {
        List<String> slices = new ArrayList<>();

        while (chunk.length() > MAX_CHUNK_SIZE) {
            // Tentar cortar em um ponto natural (. ! ? ou \n)
            int cutPoint = findCutPoint(chunk, TARGET_CHUNK_SIZE);

            slices.add(chunk.substring(0, cutPoint).trim());
            chunk = chunk.substring(cutPoint).trim();
        }

        if (!chunk.isEmpty()) {
            slices.add(chunk);
        }

        return slices;
    }

    /**
     * Encontra o melhor ponto de corte próximo ao target.
     */
    private int findCutPoint(String text, int targetPosition) {
        if (targetPosition >= text.length()) {
            return text.length();
        }

        // Procurar pontos de corte naturais (., !, ?, \n) retroativamente
        for (int i = targetPosition; i > targetPosition / 2; i--) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?' || c == '\n') {
                return i + 1;
            }
        }

        // Se não encontrar, cortar no espaço mais próximo
        for (int i = targetPosition; i > targetPosition / 2; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i + 1;
            }
        }

        // Último recurso: cortar no target
        return targetPosition;
    }

    /**
     * Cria metadata JSON para o chunk.
     */
    private JsonNode createMetadata(int chunkIndex) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("chunk_index", chunkIndex);
        return metadata;
    }

    /**
     * Estima a quantidade de tokens (aproximação: palavras * 1.3).
     */
    private int estimateTokenCount(String text) {
        int wordCount = text.split("\\s+").length;
        return (int) Math.ceil(wordCount * 1.3);
    }
}
