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
     * Resposta final gerada pelo modelo.
     */
    private String answer;

    /**
     * Chunks usados como contexto para gerar a resposta.
     */
    private List<RetrievedChunkDto> chunks;
}
