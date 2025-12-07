package com.mindmesh.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindmesh.backend.dto.MetadataV2;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

/**
 * Serviço de enriquecimento de metadados V2.
 * Usa UMA ÚNICA chamada OpenAI por documento com prompt JSON estruturado.
 */
@Slf4j
@Service
public class MetadataEnrichmentService {

    private static final String CHAT_MODEL_NAME = "gpt-4o-mini";
    private static final int MAX_TEXT_CHARS = 6000;

    private static final String ENRICHMENT_PROMPT = """
            Você é um analisador de documentos. Analise o texto abaixo e retorne APENAS um JSON válido.

            TEXTO:
            %s

            NOME DO ARQUIVO: %s

            Retorne EXATAMENTE este formato JSON (sem explicações, sem markdown, apenas o JSON):
            {
              "document_type": "<tipo: txt|pdf|markdown|code|article|report|tutorial|unknown>",
              "keywords": ["palavra1", "palavra2", "palavra3", "palavra4", "palavra5"],
              "topics": ["Tópico Principal", "Tópico Secundário"],
              "summary": "<resumo em 1 frase curta>",
              "language": "<código ISO: pt-BR|en-US|es|etc>",
              "confidence": <número de 0.0 a 1.0>
            }
            """;

    private final ChatLanguageModel chatModel;
    private final ObjectMapper objectMapper;
    private final boolean mockMode;

    public MetadataEnrichmentService() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        this.objectMapper = new ObjectMapper();

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("⚠️  OPENAI_API_KEY não definida - MetadataEnrichmentService em MODO MOCK");
            this.mockMode = true;
            this.chatModel = null;
        } else {
            this.mockMode = false;
            this.chatModel = OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(CHAT_MODEL_NAME)
                    .temperature(0.2)
                    .timeout(Duration.ofSeconds(45))
                    .build();
            log.info("MetadataEnrichmentService inicializado: {}", CHAT_MODEL_NAME);
        }
    }

    /**
     * Construtor para injeção de dependência (testes).
     */
    public MetadataEnrichmentService(ChatLanguageModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.mockMode = (chatModel == null);
    }

    /**
     * Enriquece documento com metadados usando UMA chamada OpenAI.
     * 
     * @param filename Nome do arquivo original
     * @param rawText  Texto completo extraído
     * @return MetadataV2 com todos os campos preenchidos
     */
    public MetadataV2 enrich(String filename, String rawText) {
        if (rawText == null || rawText.isBlank()) {
            log.warn("Texto vazio para enriquecimento");
            return MetadataV2.empty();
        }

        log.info("Enriquecendo metadados V2: {} ({} chars)", filename, rawText.length());
        long startTime = System.currentTimeMillis();

        if (mockMode) {
            log.debug("Usando mock para enriquecimento");
            return MetadataV2.mock(filename);
        }

        try {
            String truncatedText = truncateText(rawText);
            String prompt = String.format(ENRICHMENT_PROMPT, truncatedText, filename);

            log.debug("Chamando OpenAI para enriquecimento...");
            String response = chatModel.generate(prompt);

            MetadataV2 metadata = parseJsonResponse(response, filename);

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Metadados V2 gerados em {}ms: type={}, keywords={}, confidence={}",
                    elapsed, metadata.getDocumentType(),
                    metadata.getKeywords().size(), metadata.getConfidence());

            return metadata;

        } catch (Exception e) {
            log.error("Erro ao enriquecer metadados: {}", e.getMessage(), e);
            return MetadataV2.mock(filename); // Fallback para mock em caso de erro
        }
    }

    /**
     * Converte MetadataV2 para Map para compatibilidade com JSONB.
     */
    public Map<String, Object> enrichAsMap(String filename, String rawText) {
        return enrich(filename, rawText).toMap();
    }

    /**
     * Verifica se está em modo mock.
     */
    public boolean isMockMode() {
        return mockMode;
    }

    private String truncateText(String text) {
        if (text.length() <= MAX_TEXT_CHARS) {
            return text;
        }
        log.debug("Truncando texto de {} para {} chars", text.length(), MAX_TEXT_CHARS);
        return text.substring(0, MAX_TEXT_CHARS);
    }

    private MetadataV2 parseJsonResponse(String response, String filename) {
        try {
            // Limpar resposta (remover markdown code blocks se houver)
            String cleanJson = cleanJsonResponse(response);
            return objectMapper.readValue(cleanJson, MetadataV2.class);
        } catch (Exception e) {
            log.warn("Falha ao parsear JSON da resposta: {}. Usando fallback.", e.getMessage());
            return MetadataV2.mock(filename);
        }
    }

    private String cleanJsonResponse(String response) {
        String cleaned = response.trim();

        // Remover markdown code blocks
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return cleaned.trim();
    }
}
