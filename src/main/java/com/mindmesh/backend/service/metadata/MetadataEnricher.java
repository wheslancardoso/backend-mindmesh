package com.mindmesh.backend.service.metadata;

import com.mindmesh.backend.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Enriquece documentos com metadados gerados por IA antes do chunking.
 * Gera: summary, keywords[], topics[], document_type, stats{}, semantic_hash.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetadataEnricher {

    private final EmbeddingService embeddingService;

    /**
     * Gera metadados enriquecidos a partir do texto completo do documento.
     * Nunca lança exceções - retorna valores padrão em caso de falha.
     */
    public Map<String, Object> enrich(String text) {
        Map<String, Object> metadata = new LinkedHashMap<>();

        if (text == null || text.isBlank()) {
            log.warn("Texto vazio para enriquecimento de metadados");
            return createEmptyMetadata();
        }

        log.info("Enriquecendo documento: {} caracteres", text.length());
        long startTime = System.currentTimeMillis();

        try {
            // 1. AI-generated metadata
            metadata.put("summary", embeddingService.summarize(text));
            metadata.put("keywords", parseList(embeddingService.extractKeywords(text)));
            metadata.put("topics", parseList(embeddingService.extractTopics(text)));
            metadata.put("document_type", embeddingService.classifyDocument(text));

            // 2. Statistical metadata
            metadata.put("stats", calculateStats(text));

            // 3. Semantic hash
            metadata.put("semantic_hash", calculateSemanticHash(text));

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Metadados enriquecidos em {}ms", elapsed);

        } catch (Exception e) {
            log.error("Erro ao enriquecer metadados: {}", e.getMessage(), e);
            return createEmptyMetadata();
        }

        return metadata;
    }

    /**
     * Cria metadados vazios para fallback em caso de erro.
     */
    private Map<String, Object> createEmptyMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("summary", "");
        metadata.put("keywords", List.of());
        metadata.put("topics", List.of());
        metadata.put("document_type", "desconhecido");
        metadata.put("stats", Map.of("lines", 0, "words", 0, "characters", 0));
        metadata.put("semantic_hash", "");
        return metadata;
    }

    /**
     * Parse string de itens separados por vírgula para lista.
     */
    private List<String> parseList(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }

        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Calcula estatísticas do texto: linhas, palavras, caracteres.
     */
    private Map<String, Integer> calculateStats(String text) {
        int lines = text.split("\\r?\\n").length;
        int words = text.split("\\s+").length;
        int characters = text.length();

        return Map.of(
                "lines", lines,
                "words", words,
                "characters", characters);
    }

    /**
     * Calcula hash SHA-256 do texto, codificado em Base64.
     */
    private String calculateSemanticHash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 não suportado", e);
            return "";
        }
    }
}
