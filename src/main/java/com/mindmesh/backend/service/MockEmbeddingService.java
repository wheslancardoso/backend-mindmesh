package com.mindmesh.backend.service;

import dev.langchain4j.data.embedding.Embedding;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Random;

/**
 * Local-only embedding generator.
 * Deterministic, no external API calls.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "embedding.mock.enabled", havingValue = "true")
public class MockEmbeddingService extends EmbeddingService {

    private static final int VECTOR_SIZE = 1536;

    public MockEmbeddingService() {
        super(null); // parent constructor accepts null for mock mode
        log.info("MockEmbeddingService inicializado - embeddings determinísticos (hash-based)");
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            log.warn("Texto nulo ou vazio recebido para mock embedding, retornando array vazio");
            return new float[0];
        }

        float[] v = new float[VECTOR_SIZE];
        int seed = text.hashCode();
        Random r = new Random(seed);

        for (int i = 0; i < VECTOR_SIZE; i++) {
            v[i] = (r.nextFloat() * 2f) - 1f;
        }

        log.debug("Mock embedding gerado para texto com {} chars (seed: {})", text.length(), seed);
        return v;
    }

    public Embedding embedAsEmbedding(String text) {
        return new Embedding(embed(text));
    }
}
