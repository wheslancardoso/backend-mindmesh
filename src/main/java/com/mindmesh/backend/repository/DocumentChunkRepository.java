package com.mindmesh.backend.repository;

import com.mindmesh.backend.model.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repositório para operações de persistência e busca semântica de
 * DocumentChunk.
 * Utiliza PGVector com índice HNSW para busca por similaridade vetorial.
 */
@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    /**
     * Busca chunks similares usando distância cosseno (operador <=>) do PGVector.
     * Filtra por userId e opcionalmente por metadata JSONB.
     *
     * @param queryVector     Vetor de embedding da query (1536 dimensões)
     * @param userId          ID do usuário para filtrar documentos
     * @param metadataFilters Filtro JSONB opcional (ex: '{"type": "pdf"}'), null
     *                        para ignorar
     * @param limit           Número máximo de resultados
     * @return Lista de chunks ordenados por similaridade (mais similar primeiro)
     */
    @Query(value = """
            SELECT dc.*
            FROM document_chunks dc
            JOIN documents d ON d.id = dc.document_id
            WHERE d.user_id = :userId
              AND (:metadataFilters IS NULL OR dc.metadata @> CAST(:metadataFilters AS jsonb))
            ORDER BY dc.embedding <=> CAST(:queryVector AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<DocumentChunk> findSimilar(
            @Param("queryVector") float[] queryVector,
            @Param("userId") UUID userId,
            @Param("metadataFilters") String metadataFilters,
            @Param("limit") int limit);

    /**
     * Overload sem filtro de metadata.
     * Busca chunks similares apenas pelo userId.
     *
     * @param queryVector Vetor de embedding da query (1536 dimensões)
     * @param userId      ID do usuário para filtrar documentos
     * @param limit       Número máximo de resultados
     * @return Lista de chunks ordenados por similaridade
     */
    default List<DocumentChunk> findSimilar(float[] queryVector, UUID userId, int limit) {
        return findSimilar(queryVector, userId, null, limit);
    }

    /**
     * Busca todos os chunks de um documento específico.
     *
     * @param documentId ID do documento
     * @return Lista de chunks ordenados por índice
     */
    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(UUID documentId);

    /**
     * Remove todos os chunks de um documento.
     *
     * @param documentId ID do documento
     */
    void deleteByDocumentId(UUID documentId);
}
