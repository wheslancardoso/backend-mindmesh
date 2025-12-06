package com.mindmesh.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO para resposta do chat.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatResponseDto {

    /**
     * Resposta gerada pelo modelo.
     */
    private String answer;

    /**
     * Lista de chunks usados como contexto.
     */
    private List<RetrievedChunkDto> sources;

    /**
     * Tempo de processamento em milissegundos.
     */
    private long processingTimeMs;

    /**
     * Número de tokens usados (se disponível).
     */
    private Integer tokensUsed;
}
