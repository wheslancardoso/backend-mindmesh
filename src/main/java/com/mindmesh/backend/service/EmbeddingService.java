package com.mindmesh.backend.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Serviço responsável por transformar texto em embeddings vetoriais.
 * Utiliza a API da OpenAI com modelo text-embedding-3-small.
 */
@Slf4j
@Service
public class EmbeddingService {

    private static final String MODEL_NAME = "text-embedding-3-small";

    /**
     * Limite aproximado de caracteres para 8k tokens.
     * Estimativa: 1 token ≈ 4 caracteres em inglês, ~3 em português.
     * Usando 32k caracteres como buffer seguro para ~8k tokens.
     */
    private static final int MAX_CHARACTERS = 32_000;

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(@Value("${openai.api.key}") String apiKey) {
        this.embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(MODEL_NAME)
                .build();

        log.info("EmbeddingService inicializado com modelo: {}", MODEL_NAME);
    }

    /**
     * Converte texto em um vetor de embeddings.
     *
     * @param text Texto a ser convertido
     * @return float[] com 1536 dimensões, ou array vazio se texto for nulo/vazio
     * @throws RuntimeException se houver erro na API da OpenAI
     */
    public float[] embed(String text) {
        // Validação: texto nulo ou vazio retorna embedding vazio
        if (text == null || text.isBlank()) {
            log.warn("Texto nulo ou vazio recebido para embedding, retornando array vazio");
            return new float[0];
        }

        // Truncar inteligentemente se ultrapassar limite de tokens (~8k)
        String processedText = truncateIfNeeded(text);

        try {
            log.info("Gerando embedding para texto com {} caracteres (original: {})",
                    processedText.length(), text.length());

            Response<Embedding> response = embeddingModel.embed(processedText);
            Embedding embedding = response.content();

            float[] vector = embedding.vector();

            log.info("Embedding gerado com sucesso: {} dimensões", vector.length);
            return vector;

        } catch (Exception e) {
            log.error("Erro ao gerar embedding via OpenAI: {}", e.getMessage(), e);
            throw new RuntimeException("Falha ao gerar embedding via OpenAI: " + e.getMessage(), e);
        }
    }

    /**
     * Trunca o texto inteligentemente se ultrapassar o limite de tokens.
     * Tenta cortar em limites de sentença ou parágrafo para manter contexto.
     *
     * @param text Texto original
     * @return Texto truncado ou original se dentro do limite
     */
    private String truncateIfNeeded(String text) {
        if (text.length() <= MAX_CHARACTERS) {
            return text;
        }

        log.warn("Texto excede limite de {} caracteres ({}), truncando inteligentemente",
                MAX_CHARACTERS, text.length());

        String truncated = text.substring(0, MAX_CHARACTERS);

        // Tentar cortar no último parágrafo completo
        int lastParagraph = truncated.lastIndexOf("\n\n");
        if (lastParagraph > MAX_CHARACTERS * 0.8) {
            return truncated.substring(0, lastParagraph).trim();
        }

        // Tentar cortar na última sentença completa
        int lastSentence = Math.max(
                truncated.lastIndexOf(". "),
                Math.max(truncated.lastIndexOf("! "), truncated.lastIndexOf("? ")));
        if (lastSentence > MAX_CHARACTERS * 0.8) {
            return truncated.substring(0, lastSentence + 1).trim();
        }

        // Fallback: cortar no último espaço
        int lastSpace = truncated.lastIndexOf(" ");
        if (lastSpace > MAX_CHARACTERS * 0.9) {
            return truncated.substring(0, lastSpace).trim();
        }

        return truncated.trim();
    }

    /**
     * Converte uma lista de Double para array de float.
     * Útil para compatibilidade com APIs que retornam List<Double>.
     *
     * @param list Lista de valores Double
     * @return Array de float correspondente
     */
    private float[] toFloatArray(List<Double> list) {
        if (list == null || list.isEmpty()) {
            return new float[0];
        }

        float[] result = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Double value = list.get(i);
            result[i] = value != null ? value.floatValue() : 0.0f;
        }
        return result;
    }
}
