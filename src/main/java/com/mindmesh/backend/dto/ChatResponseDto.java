package com.mindmesh.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Resposta do chat com contexto RAG")
public class ChatResponseDto {

    @Schema(description = "Resposta final gerada pelo modelo LLM com base no contexto recuperado", example = "De acordo com os documentos, o MindMesh é uma plataforma de busca semântica que utiliza RAG (Retrieval-Augmented Generation) para responder perguntas com base em documentos do usuário.")
    private String answer;

    @Schema(description = "Lista de chunks recuperados e usados como contexto para gerar a resposta")
    private List<RetrievedChunkDto> chunks;
}
