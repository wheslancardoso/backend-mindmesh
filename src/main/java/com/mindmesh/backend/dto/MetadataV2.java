package com.mindmesh.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * POJO para metadados enriquecidos de documento V2.
 * Gerado por uma única chamada OpenAI com prompt JSON estruturado.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetadataV2 {

    /**
     * Tipo do documento detectado.
     * Valores: txt, pdf, markdown, code, article, report, tutorial, unknown
     */
    @JsonProperty("document_type")
    private String documentType;

    /**
     * Palavras-chave relevantes (~5 itens).
     */
    private List<String> keywords;

    /**
     * Tópicos amplos do documento.
     * Ex: "IA", "backend", "devops", "jurídico"
     */
    private List<String> topics;

    /**
     * Resumo do documento em 1 frase.
     */
    private String summary;

    /**
     * Código ISO do idioma detectado.
     * Ex: "pt-BR", "en-US", "es"
     */
    private String language;

    /**
     * Confiança da IA na análise (0.0 - 1.0).
     */
    private Double confidence;

    /**
     * Converte para Map<String, Object> para compatibilidade com JSONB.
     */
    public Map<String, Object> toMap() {
        return Map.of(
                "document_type", documentType != null ? documentType : "unknown",
                "keywords", keywords != null ? keywords : List.of(),
                "topics", topics != null ? topics : List.of(),
                "summary", summary != null ? summary : "",
                "language", language != null ? language : "unknown",
                "confidence", confidence != null ? confidence : 0.0);
    }

    /**
     * Cria instância vazia para fallback.
     */
    public static MetadataV2 empty() {
        return MetadataV2.builder()
                .documentType("unknown")
                .keywords(List.of())
                .topics(List.of())
                .summary("")
                .language("unknown")
                .confidence(0.0)
                .build();
    }

    /**
     * Cria instância mock para testes.
     */
    public static MetadataV2 mock(String filename) {
        String type = detectTypeFromFilename(filename);
        return MetadataV2.builder()
                .documentType(type)
                .keywords(List.of("documento", "análise", "texto", "mock", "teste"))
                .topics(List.of("Processamento de Documentos", "RAG"))
                .summary("[MOCK] Documento processado automaticamente para análise.")
                .language("pt-BR")
                .confidence(0.8)
                .build();
    }

    private static String detectTypeFromFilename(String filename) {
        if (filename == null)
            return "unknown";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".txt"))
            return "txt";
        if (lower.endsWith(".pdf"))
            return "pdf";
        if (lower.endsWith(".md"))
            return "markdown";
        if (lower.endsWith(".java") || lower.endsWith(".py") || lower.endsWith(".js")
                || lower.endsWith(".ts") || lower.endsWith(".go"))
            return "code";
        if (lower.endsWith(".html") || lower.endsWith(".htm"))
            return "article";
        if (lower.endsWith(".doc") || lower.endsWith(".docx"))
            return "report";
        return "unknown";
    }
}
