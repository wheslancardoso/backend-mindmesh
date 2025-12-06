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
     * ID do usuário dono dos documentos.
     */
    private UUID userId;

    /**
     * Pergunta do usuário.
     */
    private String question;

    /**
     * Filtros de metadata em formato JSON (opcional).
     * Exemplo: '{"source": "pdf", "type": "report"}'
     */
    private String metadataFilters;

    /**
     * Número máximo de chunks a recuperar (opcional, default: 8).
     */
    private Integer limit;

    /**
     * Retorna o limite efetivo (default 8 se null).
     */
    public int getEffectiveLimit() {
        return limit != null && limit > 0 ? limit : 8;
    }
}
