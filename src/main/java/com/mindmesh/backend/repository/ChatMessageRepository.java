package com.mindmesh.backend.repository;

import com.mindmesh.backend.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repositório para mensagens de chat.
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /**
     * Busca mensagens de uma sessão ordenadas por data.
     */
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    /**
     * Conta mensagens de uma sessão.
     */
    long countBySessionId(UUID sessionId);

    /**
     * Remove todas as mensagens de uma sessão.
     */
    void deleteBySessionId(UUID sessionId);
}
