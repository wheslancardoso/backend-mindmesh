package com.mindmesh.backend.repository;

import com.mindmesh.backend.dto.ChunkSearchResult;
import com.mindmesh.backend.model.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repositório para DocumentChunk com busca vetorial PGVector.
 * Extende DocumentChunkRepositoryCustom para método findSimilar customizado.
 */
@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID>, DocumentChunkRepositoryCustom {

  /**
   * Overload sem filtro de metadata.
   */
  default List<ChunkSearchResult> findSimilar(float[] queryVector, UUID userId, int limit) {
    return findSimilar(queryVector, userId, null, limit);
  }

  /**
   * Busca chunks de um documento específico ordenados por índice.
   */
  List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(UUID documentId);

  /**
   * Remove todos os chunks de um documento.
   * Usa query nativa para evitar carregar entidades com tipo vector.
   */
  @Modifying
  @Query(value = "DELETE FROM document_chunks WHERE document_id = :documentId", nativeQuery = true)
  void deleteByDocumentId(@Param("documentId") UUID documentId);

  /**
   * Conta chunks de um documento.
   */
  long countByDocumentId(UUID documentId);
}
