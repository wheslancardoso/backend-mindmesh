package com.mindmesh.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO representando um chunk recuperado pela busca vetorial.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetrievedChunkDto {

    /**
     * ID do chunk.
     */
    private UUID id;

    /**
     * ID do documento pai.
     */
    private UUID documentId;

    /**
     * Trecho do conteúdo (snippet).
     */
    private String snippet;

    /**
     * Índice do chunk no documento.
     */
    private Integer chunkIndex;

    /**
     * Score de similaridade (opcional, se disponível).
     */
    private Double similarityScore;
}
