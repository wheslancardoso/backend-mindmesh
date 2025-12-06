package com.mindmesh.backend.dto;

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
public class ChatRequestDto {

    /**
     * Pergunta do usuário.
     */
    private String question;

    /**
     * ID do usuário (para filtrar documentos).
     */
    private UUID userId;

    /**
     * Filtro de metadata opcional (JSON string).
     * Exemplo: '{"source": "pdf"}'
     */
    private String metadataFilter;

    /**
     * Número máximo de chunks a recuperar (default: 8).
     */
    private Integer limit;

    public int getEffectiveLimit() {
        return limit != null && limit > 0 ? limit : 8;
    }
}
