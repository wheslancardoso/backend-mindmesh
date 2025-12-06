package com.mindmesh.backend.repository;

import com.mindmesh.backend.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repositório para operações de persistência de Document.
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    /**
     * Busca todos os documentos de um usuário.
     *
     * @param userId ID do usuário
     * @return Lista de documentos do usuário
     */
    List<Document> findByUserId(UUID userId);

    /**
     * Busca documentos de um usuário ordenados por data de criação (mais recente
     * primeiro).
     *
     * @param userId ID do usuário
     * @return Lista de documentos ordenados
     */
    List<Document> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
