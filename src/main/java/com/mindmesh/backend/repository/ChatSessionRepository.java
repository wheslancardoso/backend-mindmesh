package com.mindmesh.backend.repository;

import com.mindmesh.backend.model.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repositório para sessões de chat.
 */
@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    /**
     * Busca sessões do usuário ordenadas por data de criação (desc).
     */
    List<ChatSession> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Conta sessões do usuário.
     */
    long countByUserId(UUID userId);
}
