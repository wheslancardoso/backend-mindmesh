package com.mindmesh.backend.repository;

import com.mindmesh.backend.dto.ChunkSearchResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Implementação customizada para busca vetorial com PGVector.
 * Usa SQL nativo com projeção para evitar problemas com hypersistence-utils e o
 * tipo vector.
 */
@Slf4j
@Repository
public class DocumentChunkRepositoryCustomImpl implements DocumentChunkRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @SuppressWarnings("unchecked")
    public List<ChunkSearchResult> findSimilar(float[] queryVector, UUID userId, String metadataFilters, int limit) {
        try {
            // Converter float[] para string formato PostgreSQL vector literal
            String vectorLiteral = arrayToVectorLiteral(queryVector);

            // Query com projeção explícita - NÃO retorna o campo embedding
            String sql = """
                        SELECT dc.id, dc.document_id, dc.chunk_index, dc.content, dc.token_count,
                               CAST(dc.metadata AS TEXT) as metadata
                        FROM document_chunks dc
                        JOIN documents d ON d.id = dc.document_id
                        WHERE d.user_id = :userId
                    """;

            if (metadataFilters != null && !metadataFilters.isBlank()) {
                sql += " AND dc.metadata @> CAST(:metadataFilters AS jsonb)";
            }

            sql += " ORDER BY dc.embedding <=> CAST(:queryVector AS vector) LIMIT :limit";

            var query = entityManager.createNativeQuery(sql)
                    .setParameter("userId", userId)
                    .setParameter("queryVector", vectorLiteral)
                    .setParameter("limit", limit);

            if (metadataFilters != null && !metadataFilters.isBlank()) {
                query.setParameter("metadataFilters", metadataFilters);
            }

            List<Object[]> results = query.getResultList();
            List<ChunkSearchResult> chunks = new ArrayList<>();

            for (Object[] row : results) {
                ChunkSearchResult chunk = ChunkSearchResult.builder()
                        .id((UUID) row[0])
                        .documentId((UUID) row[1])
                        .chunkIndex(((Number) row[2]).intValue())
                        .content((String) row[3])
                        .tokenCount(((Number) row[4]).intValue())
                        .metadata((String) row[5])
                        .build();
                chunks.add(chunk);
            }

            log.debug("findSimilar retornou {} chunks para userId={}", chunks.size(), userId);
            return chunks;

        } catch (Exception e) {
            log.error("Erro em findSimilar: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Converte float[] para formato literal PostgreSQL vector: '[0.1,0.2,0.3]'
     */
    private String arrayToVectorLiteral(float[] arr) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0)
                sb.append(",");
            sb.append(arr[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
