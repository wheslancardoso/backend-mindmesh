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
     * Trecho reduzido do conteúdo (ex: primeiros 300 chars).
     */
    private String contentSnippet;

    /**
     * Índice do chunk no documento.
     */
    private Integer chunkIndex;

    /**
     * Contagem aproximada de tokens.
     */
    private Integer tokenCount;
}
