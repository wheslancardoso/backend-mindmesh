package com.mindmesh.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO de requisição de chat (session-based).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Requisição de chat com sessão")
public class ChatRequestDto {

    @Schema(description = "ID da sessão de chat (cria nova se null)", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID sessionId;

    @Schema(description = "ID do usuário", example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID userId;

    @Schema(description = "Mensagem do usuário", example = "O que é o MindMesh?", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;

    @Schema(description = "Filtros de metadata JSONB", example = "{\"type\": \"pdf\"}")
    private String metadataFilters;

    @Schema(description = "Limite de chunks a recuperar", example = "5", minimum = "1", maximum = "20")
    @Builder.Default
    private Integer limit = 5;

    public int getEffectiveLimit() {
        return (limit != null && limit > 0 && limit <= 20) ? limit : 5;
    }
}
