package com.mindmesh.backend.controller;

import com.mindmesh.backend.dto.ChatRequestDto;
import com.mindmesh.backend.dto.ChatResponseDto;
import com.mindmesh.backend.service.ChatService;
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
public class ChatController {

    private final ChatService chatService;

    /**
     * Endpoint principal de chat.
     * Recebe uma pergunta e retorna a resposta gerada via RAG.
     *
     * @param request Requisição contendo userId, question e filtros opcionais
     * @return Resposta com answer e chunks usados como contexto
     */
    @PostMapping
    public ResponseEntity<ChatResponseDto> chat(@RequestBody ChatRequestDto request) {
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
