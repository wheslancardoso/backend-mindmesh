package com.mindmesh.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindmesh.backend.dto.ChatRequestDto;
import com.mindmesh.backend.dto.ChatResponseDto;
import com.mindmesh.backend.dto.RetrievedChunkDto;
import com.mindmesh.backend.service.ChatService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes do ChatController usando MockMvc.
 * Alinhado com schema final (message em vez de question).
 */
@WebMvcTest(ChatController.class)
class ChatControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @MockBean
        private ChatService chatService;

        private static final UUID TEST_USER_ID = UUID.randomUUID();
        private static final UUID TEST_DOCUMENT_ID = UUID.randomUUID();
        private static final UUID TEST_SESSION_ID = UUID.randomUUID();

        private ChatResponseDto createMockResponse(String answer, int chunkCount) {
                List<RetrievedChunkDto> chunks = java.util.stream.IntStream.range(0, chunkCount)
                                .mapToObj(i -> RetrievedChunkDto.builder()
                                                .id(UUID.randomUUID())
                                                .documentId(TEST_DOCUMENT_ID)
                                                .contentSnippet("Snippet do chunk " + i)
                                                .chunkIndex(i)
                                                .tokenCount(50)
                                                .build())
                                .toList();

                return ChatResponseDto.builder()
                                .sessionId(TEST_SESSION_ID)
                                .answer(answer)
                                .chunks(chunks)
                                .messageId(UUID.randomUUID())
                                .build();
        }

        @Test
        @DisplayName("POST /api/chat - Deve retornar resposta com sucesso")
        void testChat_success() throws Exception {
                ChatRequestDto request = ChatRequestDto.builder()
                                .userId(TEST_USER_ID)
                                .message("Qual é o conteúdo dos documentos?")
                                .limit(5)
                                .build();

                ChatResponseDto mockResponse = createMockResponse(
                                "Esta é a resposta gerada pelo modelo.",
                                3);

                when(chatService.chat(any(ChatRequestDto.class))).thenReturn(mockResponse);

                mockMvc.perform(post("/api/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.answer").value("Esta é a resposta gerada pelo modelo."))
                                .andExpect(jsonPath("$.sessionId").value(TEST_SESSION_ID.toString()))
                                .andExpect(jsonPath("$.chunks", hasSize(3)));

                verify(chatService).chat(any(ChatRequestDto.class));
        }

        @Test
        @DisplayName("POST /api/chat - Deve retornar 400 para mensagem nula")
        void testChat_invalidRequest_nullMessage() throws Exception {
                ChatRequestDto request = ChatRequestDto.builder()
                                .userId(TEST_USER_ID)
                                .message(null)
                                .build();

                when(chatService.chat(any(ChatRequestDto.class)))
                                .thenThrow(new IllegalArgumentException("A mensagem não pode estar vazia"));

                mockMvc.perform(post("/api/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").value("Requisição inválida"));
        }

        @Test
        @DisplayName("POST /api/chat - Deve retornar 400 para mensagem vazia")
        void testChat_invalidRequest_emptyMessage() throws Exception {
                ChatRequestDto request = ChatRequestDto.builder()
                                .userId(TEST_USER_ID)
                                .message("")
                                .build();

                when(chatService.chat(any(ChatRequestDto.class)))
                                .thenThrow(new IllegalArgumentException("A mensagem não pode estar vazia"));

                mockMvc.perform(post("/api/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /api/chat - Deve retornar 400 para userId nulo")
        void testChat_invalidRequest_nullUserId() throws Exception {
                ChatRequestDto request = ChatRequestDto.builder()
                                .userId(null)
                                .message("Qual é o conteúdo?")
                                .build();

                when(chatService.chat(any(ChatRequestDto.class)))
                                .thenThrow(new IllegalArgumentException("userId é obrigatório"));

                mockMvc.perform(post("/api/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(containsString("userId")));
        }

        @Test
        @DisplayName("POST /api/chat - Deve retornar 500 para erro interno")
        void testChat_internalError() throws Exception {
                ChatRequestDto request = ChatRequestDto.builder()
                                .userId(TEST_USER_ID)
                                .message("Pergunta que causa erro")
                                .build();

                when(chatService.chat(any(ChatRequestDto.class)))
                                .thenThrow(new RuntimeException("Erro ao conectar com OpenAI"));

                mockMvc.perform(post("/api/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isInternalServerError())
                                .andExpect(jsonPath("$.error").value("Erro interno"));
        }

        @Test
        @DisplayName("POST /api/chat - Deve retornar array vazio quando não há chunks")
        void testChat_returnsEmptySources() throws Exception {
                ChatRequestDto request = ChatRequestDto.builder()
                                .userId(TEST_USER_ID)
                                .message("Pergunta sem documentos relevantes")
                                .build();

                ChatResponseDto mockResponse = ChatResponseDto.builder()
                                .sessionId(TEST_SESSION_ID)
                                .answer("Não encontrei documentos relevantes.")
                                .chunks(List.of())
                                .messageId(UUID.randomUUID())
                                .build();

                when(chatService.chat(any(ChatRequestDto.class))).thenReturn(mockResponse);

                mockMvc.perform(post("/api/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.chunks", hasSize(0)));
        }

        @Test
        @DisplayName("POST /api/chat - Deve aceitar metadataFilters")
        void testChat_withMetadataFilters() throws Exception {
                UUID userId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

                ChatResponseDto mockResponse = ChatResponseDto.builder()
                                .sessionId(TEST_SESSION_ID)
                                .answer("ok")
                                .chunks(List.of())
                                .messageId(UUID.randomUUID())
                                .build();

                when(chatService.chat(any(ChatRequestDto.class))).thenReturn(mockResponse);

                String requestBody = """
                                {
                                    "userId": "123e4567-e89b-12d3-a456-426614174000",
                                    "message": "Teste com metadados",
                                    "metadataFilters": "{\\"type\\": \\"pdf\\"}",
                                    "limit": 5
                                }
                                """;

                mockMvc.perform(post("/api/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                                .andExpect(status().isOk());

                verify(chatService).chat(argThat(req -> req.getUserId().equals(userId) &&
                                "Teste com metadados".equals(req.getMessage())));
        }

        @Test
        @DisplayName("POST /api/chat - Deve aceitar sessionId existente")
        void testChat_withExistingSession() throws Exception {
                ChatRequestDto request = ChatRequestDto.builder()
                                .userId(TEST_USER_ID)
                                .sessionId(TEST_SESSION_ID)
                                .message("Continuando conversa")
                                .build();

                ChatResponseDto mockResponse = createMockResponse("Continuação da resposta", 1);

                when(chatService.chat(any(ChatRequestDto.class))).thenReturn(mockResponse);

                mockMvc.perform(post("/api/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.sessionId").value(TEST_SESSION_ID.toString()));
        }
}
