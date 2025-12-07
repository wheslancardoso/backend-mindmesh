package com.mindmesh.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Representa uma mensagem em uma sessão de chat.
 * Inclui referência aos chunks usados para rastreabilidade.
 */
@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false, insertable = false, updatable = false)
    private UUID sessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    @JsonIgnore
    private ChatSession session;

    @Column(name = "role", nullable = false, length = 20)
    private String role; // "user" | "assistant"

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * IDs dos chunks usados como contexto para esta resposta.
     * Permite rastreabilidade e feedback loop.
     */
    @Column(name = "used_chunk_ids", columnDefinition = "uuid[]")
    private UUID[] usedChunkIds;

    /**
     * Score de feedback do usuário (-1, 0, 1 ou 1-5).
     */
    @Column(name = "feedback_score")
    private Integer feedbackScore;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.session != null) {
            this.sessionId = this.session.getId();
        }
    }
}
