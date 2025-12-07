package com.mindmesh.backend.repository;

import com.mindmesh.backend.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório para operações com documentos.
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    /**
     * Busca documentos do usuário ordenados por data de criação (desc).
     */
    List<Document> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Verifica se documento existe para usuário com o mesmo hash.
     */
    boolean existsByUserIdAndFileHash(UUID userId, String fileHash);

    /**
     * Busca documento por userId e fileHash (deduplicação).
     */
    Optional<Document> findByUserIdAndFileHash(UUID userId, String fileHash);

    /**
     * Conta documentos do usuário.
     */
    long countByUserId(UUID userId);
}
