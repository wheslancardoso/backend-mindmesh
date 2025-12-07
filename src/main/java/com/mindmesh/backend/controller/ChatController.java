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
 * Controller REST para chat RAG com sessões.
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "Chat", description = "Chat com RAG usando busca semântica + LLM")
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "Enviar mensagem ao chat", description = """
            Processa uma mensagem usando RAG:
            1. Cria ou reutiliza sessão
            2. Gera embedding da pergunta
            3. Busca chunks similares no PGVector
            4. Gera resposta via LLM
            5. Persiste mensagens na sessão
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resposta gerada", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ChatResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida"),
            @ApiResponse(responseCode = "500", description = "Erro interno")
    })
    @PostMapping
    public ResponseEntity<ChatResponseDto> chat(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Dados da requisição", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = ChatRequestDto.class), examples = @ExampleObject(value = """
                    {
                        "userId": "550e8400-e29b-41d4-a716-446655440000",
                        "message": "O que é o MindMesh?",
                        "sessionId": null,
                        "limit": 5
                    }
                    """))) @RequestBody ChatRequestDto request) {

        log.info("Chat - userId: {}, sessionId: {}, message: '{}'",
                request.getUserId(),
                request.getSessionId(),
                truncateForLog(request.getMessage()));

        ChatResponseDto response = chatService.chat(request);

        log.info("Resposta gerada - sessionId: {}, chunks: {}",
                response.getSessionId(),
                response.getChunks() != null ? response.getChunks().size() : 0);

        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleValidationError(IllegalArgumentException e) {
        log.warn("Erro de validação: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Requisição inválida",
                "message", e.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeError(RuntimeException e) {
        log.error("Erro ao processar chat: {}", e.getMessage(), e);
        return ResponseEntity.internalServerError().body(Map.of(
                "error", "Erro interno",
                "message", "Falha ao processar a requisição"));
    }

    private String truncateForLog(String text) {
        if (text == null)
            return "";
        if (text.length() <= 50)
            return text;
        return text.substring(0, 47) + "...";
    }
}
