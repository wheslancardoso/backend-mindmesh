package com.mindmesh.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO para chunk recuperado na busca semântica.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Chunk de documento recuperado pela busca semântica")
public class RetrievedChunkDto {

    @Schema(description = "ID único do chunk", example = "550e8400-e29b-41d4-a716-446655440001")
    private UUID id;

    @Schema(description = "ID do documento pai", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID documentId;

    @Schema(description = "Trecho do conteúdo do chunk", example = "MindMesh é uma plataforma de RAG...")
    private String contentSnippet;

    @Schema(description = "Índice do chunk no documento", example = "0")
    private Integer chunkIndex;

    @Schema(description = "Contagem de tokens do chunk", example = "156")
    private Integer tokenCount;
}
