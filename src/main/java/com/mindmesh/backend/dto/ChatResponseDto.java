package com.mindmesh.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * DTO de resposta do chat.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resposta do chat RAG")
public class ChatResponseDto {

    @Schema(description = "ID da sessão de chat")
    private UUID sessionId;

    @Schema(description = "Resposta gerada pelo assistente")
    private String answer;

    @Schema(description = "Chunks de contexto utilizados")
    private List<RetrievedChunkDto> chunks;

    @Schema(description = "ID da mensagem persistida")
    private UUID messageId;
}
