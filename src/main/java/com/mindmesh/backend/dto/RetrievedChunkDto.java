package com.mindmesh.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Chunk de documento recuperado pela busca semântica")
public class RetrievedChunkDto {

    @Schema(description = "ID único do chunk (UUID)", example = "550e8400-e29b-41d4-a716-446655440001")
    private UUID id;

    @Schema(description = "ID do documento pai ao qual este chunk pertence", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID documentId;

    @Schema(description = "Trecho reduzido do conteúdo do chunk (primeiros ~300 caracteres)", example = "O MindMesh é uma plataforma de busca semântica que utiliza técnicas de RAG para processar documentos...")
    private String contentSnippet;

    @Schema(description = "Índice sequencial do chunk dentro do documento (começa em 0)", example = "3", minimum = "0")
    private Integer chunkIndex;

    @Schema(description = "Contagem aproximada de tokens do chunk", example = "156", minimum = "0")
    private Integer tokenCount;
}
