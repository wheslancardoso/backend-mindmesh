package com.mindmesh.backend.controller;

import com.mindmesh.backend.dto.ChatRequestDto;
import com.mindmesh.backend.dto.ChatResponseDto;
import com.mindmesh.backend.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller REST para o fluxo de chat RAG.
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // TODO: Configurar CORS adequadamente em produção
@Tag(name = "Chat", description = """
        Chat com RAG (Retrieval-Augmented Generation).

        Permite fazer perguntas usando linguagem natural e receber respostas
        contextualizadas com base nos documentos do usuário.
        Utiliza busca semântica vetorial + LLM (OpenAI GPT).
        """)
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "Enviar pergunta ao chat", description = """
            Processa uma pergunta do usuário usando o fluxo RAG completo:

            1. **Embedding**: Converte a pergunta em vetor usando OpenAI
            2. **Busca vetorial**: Encontra os chunks mais relevantes no PGVector
            3. **Contextualização**: Monta o prompt com os chunks recuperados
            4. **Geração**: Envia ao LLM (GPT) para gerar a resposta final

            A resposta inclui tanto o texto gerado quanto os chunks usados como fonte.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resposta gerada com sucesso", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ChatResponseDto.class), examples = @ExampleObject(value = """
                    {
                        "answer": "De acordo com os documentos, o MindMesh é uma plataforma de busca semântica...",
                        "chunks": [
                            {
                                "id": "550e8400-e29b-41d4-a716-446655440001",
                                "documentId": "550e8400-e29b-41d4-a716-446655440000",
                                "contentSnippet": "MindMesh é uma plataforma de RAG...",
                                "chunkIndex": 0,
                                "tokenCount": 156
                            }
                        ]
                    }
                    """))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida (pergunta vazia ou userId ausente)", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                    {
                        "error": "Requisição inválida",
                        "message": "A pergunta não pode estar vazia"
                    }
                    """))),
            @ApiResponse(responseCode = "500", description = "Erro interno ao processar pergunta", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                    {
                        "error": "Erro interno",
                        "message": "Falha ao processar a requisição. Tente novamente."
                    }
                    """)))
    })
    @PostMapping
    public ResponseEntity<ChatResponseDto> chat(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Dados da requisição de chat", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = ChatRequestDto.class), examples = @ExampleObject(value = """
                    {
                        "userId": "550e8400-e29b-41d4-a716-446655440000",
                        "question": "O que é o MindMesh?",
                        "metadataFilters": null,
                        "limit": 5
                    }
                    """))) @RequestBody ChatRequestDto request) {

        log.info("Recebida requisição de chat - userId: {}, pergunta: '{}'",
                request.getUserId(),
                truncateForLog(request.getQuestion()));

        ChatResponseDto response = chatService.chat(request);

        log.info("Resposta gerada com {} chunks de contexto",
                response.getChunks() != null ? response.getChunks().size() : 0);

        return ResponseEntity.ok(response);
    }

    /**
     * Handler de exceções para IllegalArgumentException (validação).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleValidationError(IllegalArgumentException e) {
        log.warn("Erro de validação: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Requisição inválida",
                "message", e.getMessage()));
    }

    /**
     * Handler de exceções genéricas.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeError(RuntimeException e) {
        log.error("Erro ao processar chat: {}", e.getMessage(), e);
        return ResponseEntity.internalServerError().body(Map.of(
                "error", "Erro interno",
                "message", "Falha ao processar a requisição. Tente novamente."));
    }

    /**
     * Trunca texto para logging.
     */
    private String truncateForLog(String text) {
        if (text == null)
            return "";
        if (text.length() <= 50)
            return text;
        return text.substring(0, 47) + "...";
    }
}
