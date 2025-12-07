package com.mindmesh.backend.model;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.array.FloatArrayType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Representa um chunk de documento com embedding vetorial para busca semântica.
 * Armazena fragmentos de texto com seus embeddings para RAG.
 * 
 * O campo embedding usa o tipo VECTOR(1536) do PGVector para busca por
 * similaridade.
 */
@Entity
@Table(name = "document_chunks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentChunk {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * Embedding vetorial de 1536 dimensões gerado pelo modelo OpenAI.
     * Armazenado como tipo VECTOR(1536) no PostgreSQL via PGVector.
     */
    @Type(FloatArrayType.class)
    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private float[] embedding;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private JsonNode metadata;

    @Column(name = "token_count")
    private Integer tokenCount;

    @Column(name = "chunk_index")
    private Integer chunkIndex;

    /**
     * Gera UUID automaticamente antes de persistir se não estiver definido.
     */
    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
    }
}
