package com.mindmesh.backend.repository;

import com.mindmesh.backend.dto.ChunkSearchResult;

import java.util.List;
import java.util.UUID;

/**
 * Interface customizada para busca vetorial.
 */
public interface DocumentChunkRepositoryCustom {

    /**
     * Busca chunks similares usando distância cosseno do PGVector.
     * Retorna DTOs sem o campo embedding para evitar problemas com
     * hypersistence-utils.
     */
    List<ChunkSearchResult> findSimilar(float[] queryVector, UUID userId, String metadataFilters, int limit);
}
