package com.mindmesh.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO de projeção para chunks retornados pela busca vetorial.
 * Exclui o campo embedding para evitar problemas com hypersistence-utils.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkSearchResult {

    private UUID id;
    private UUID documentId;
    private Integer chunkIndex;
    private String content;
    private Integer tokenCount;
    private String metadata; // JSON como String para evitar conversão
}
