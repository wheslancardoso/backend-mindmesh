package com.mindmesh.backend.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Serviço responsável por transformar texto em embeddings vetoriais.
 * Utiliza a API da OpenAI via LangChain4j.
 */
@Slf4j
@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(@Value("${openai.api.key}") String apiKey) {
        this.embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName("text-embedding-3-small")
                .build();

        log.info("EmbeddingService inicializado com modelo: text-embedding-3-small");
    }

    /**
     * Converte texto em um vetor de embeddings.
     *
     * @param text Texto a ser convertido
     * @return float[] com 1536 dimensões
     * @throws IllegalArgumentException se texto for nulo ou vazio
     * @throws RuntimeException         se houver erro na API
     */
    public float[] embed(String text) {
        // Validação de input
        if (text == null || text.isBlank()) {
            log.warn("Texto nulo ou vazio recebido para embedding");
            throw new IllegalArgumentException("Texto não pode ser nulo ou vazio");
        }

        try {
            log.info("Gerando embedding para texto com {} caracteres", text.length());

            Response<Embedding> response = embeddingModel.embed(text);
            Embedding embedding = response.content();

            // Converter de float[] (LangChain4j retorna float[])
            float[] vector = embedding.vector();

            log.info("Embedding gerado com sucesso: {} dimensões", vector.length);
            return vector;

        } catch (Exception e) {
            log.error("Erro ao gerar embedding: {}", e.getMessage(), e);
            throw new RuntimeException("Falha ao gerar embedding via OpenAI: " + e.getMessage(), e);
        }
    }
}
