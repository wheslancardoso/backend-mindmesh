package com.mindmesh.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO para requisição de chat.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Requisição de chat com contexto RAG")
public class ChatRequestDto {

    @Schema(description = "ID do usuário dono dos documentos (UUID)", example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID userId;

    @Schema(description = "Pergunta do usuário em linguagem natural", example = "Qual é o objetivo principal do projeto MindMesh?", requiredMode = Schema.RequiredMode.REQUIRED)
    private String question;

    @Schema(description = """
            Filtros de metadata em formato JSON (opcional).
            Permite filtrar chunks por propriedades específicas.
            """, example = "{\"source\": \"pdf\", \"type\": \"report\"}", nullable = true)
    private String metadataFilters;

    @Schema(description = "Número máximo de chunks a recuperar (default: 8)", example = "5", minimum = "1", maximum = "20", defaultValue = "8")
    private Integer limit;

    /**
     * Retorna o limite efetivo (default 8 se null).
     */
    @Schema(hidden = true)
    public int getEffectiveLimit() {
        return limit != null && limit > 0 ? limit : 8;
    }
}
