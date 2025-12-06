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
 * Testa apenas a camada web sem subir contexto completo.
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
        private static final UUID TEST_CHUNK_ID = UUID.randomUUID();

        /**
         * Cria uma resposta de chat simulada.
         */
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
                                .answer(answer)
                                .chunks(chunks)
                                .build();
        }

        @Test
        @DisplayName("POST /api/chat - Deve retornar resposta com sucesso")
        void testChat_success() throws Exception {
                // Arrange
                ChatRequestDto request = ChatRequestDto.builder()
                                .userId(TEST_USER_ID)
                                .question("Qual é o conteúdo dos documentos?")
                                .metadataFilters(null)
                                .limit(5)
                                .build();

                ChatResponseDto mockResponse = createMockResponse(
                                "Esta é a resposta gerada pelo modelo baseada nos documentos.",
                                3);

                when(chatService.chat(any(ChatRequestDto.class))).thenReturn(mockResponse);

                // Act & Assert
                mockMvc.perform(post("/api/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                                .andExpect(jsonPath("$.answer")
                                                .value("Esta é a resposta gerada pelo modelo baseada nos documentos."))
                                .andExpect(jsonPath("$.chunks").isArray())
                                .andExpect(jsonPath("$.chunks", hasSize(3)))
                                .andExpect(jsonPath("$.chunks[0].contentSnippet").value("Snippet do chunk 0"))
                                .andExpect(jsonPath("$.chunks[0].chunkIndex").value(0))
                                .andExpect(jsonPath("$.chunks[0].tokenCount").value(50));

                // Verificar que o service foi chamado
                verify(chatService).chat(any(ChatRequestDto.class));

                System.out.println("Chat com sucesso - 3 chunks retornados");
        }

        @Test
        @DisplayName("POST /api/chat - Deve retornar 400 para pergunta nula")
        void testChat_invalidRequest_nullQuestion() throws Exception {
                // Arrange
                ChatRequestDto request = ChatRequestDto.builder()
                                .userId(TEST_USER_ID)
                                .question(null)
                                .build();

                when(chatService.chat(any(ChatRequestDto.class)))
                                .thenThrow(new IllegalArgumentException("A pergunta não pode estar vazia"));

                // Act & Assert
                mockMvc.perform(post("/api/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").value("Requisição inválida"))
                                .andExpect(jsonPath("$.message").value(containsString("pergunta")));

                System.out.println("Pergunta nula rejeitada com status 400");
        }

        @Test
        @DisplayName("POST /api/chat - Deve retornar 400 para pergunta vazia")
        void testChat_invalidRequest_emptyQuestion() throws Exception {
                // Arrange
                ChatRequestDto request = ChatRequestDto.builder()
                                .userId(TEST_USER_ID)
                                .question("")
                                .build();

                when(chatService.chat(any(ChatRequestDto.class)))
                                .thenThrow(new IllegalArgumentException("A pergunta não pode estar vazia"));

                // Act & Assert
                mockMvc.perform(post("/api/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());

                System.out.println("Pergunta vazia rejeitada com status 400");
        }

        @Test
        @DisplayName("POST /api/chat - Deve retornar 400 para userId nulo")
        void testChat_invalidRequest_nullUserId() throws Exception {
                // Arrange
                ChatRequestDto request = ChatRequestDto.builder()
                                .userId(null)
                                .question("Qual é o conteúdo?")
                                .build();

                when(chatService.chat(any(ChatRequestDto.class)))
                                .thenThrow(new IllegalArgumentException("userId é obrigatório"));

                // Act & Assert
                mockMvc.perform(post("/api/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").value("Requisição inválida"))
                                .andExpect(jsonPath("$.message").value(containsString("userId")));

                System.out.println("UserId nulo rejeitado com status 400");
        }

        @Test
        @DisplayName("POST /api/chat - Deve retornar 500 para erro interno")
        void testChat_internalError() throws Exception {
                // Arrange
                ChatRequestDto request = ChatRequestDto.builder()
                                .userId(TEST_USER_ID)
                                .question("Pergunta que causa erro")
                                .build();

                when(chatService.chat(any(ChatRequestDto.class)))
                                .thenThrow(new RuntimeException("Erro ao conectar com OpenAI"));

                // Act & Assert
                mockMvc.perform(post("/api/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isInternalServerError())
                                .andExpect(jsonPath("$.error").value("Erro interno"))
                                .andExpect(jsonPath("$.message")
                                                .value("Falha ao processar a requisição. Tente novamente."));

                System.out.println("Erro interno retornou status 500");
        }

        @Test
        @DisplayName("POST /api/chat - Deve retornar array vazio quando não há chunks")
        void testChat_returnsEmptySources() throws Exception {
                // Arrange
                ChatRequestDto request = ChatRequestDto.builder()
                                .userId(TEST_USER_ID)
                                .question("Pergunta sem documentos relevantes")
                                .build();

                ChatResponseDto mockResponse = ChatResponseDto.builder()
                                .answer("Não encontrei documentos relevantes para responder sua pergunta.")
                                .chunks(List.of()) // Lista vazia
                                .build();

                when(chatService.chat(any(ChatRequestDto.class))).thenReturn(mockResponse);

                // Act & Assert
                mockMvc.perform(post("/api/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.answer").value(containsString("Não encontrei")))
                                .andExpect(jsonPath("$.chunks").isArray())
                                .andExpect(jsonPath("$.chunks", hasSize(0)));

                System.out.println("Resposta sem chunks retornada corretamente");
        }

        @Test
        @DisplayName("POST /api/chat - Deve aceitar metadataFilters")
        void testChat_withMetadataFilters() throws Exception {
                // Arrange
                UUID userId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

                ChatResponseDto mockResponse = ChatResponseDto.builder()
                                .answer("ok")
                                .chunks(List.of())
                                .build();

                when(chatService.chat(any(ChatRequestDto.class))).thenReturn(mockResponse);

                String requestBody = """
                                {
                                    "userId": "123e4567-e89b-12d3-a456-426614174000",
                                    "question": "Teste com metadados",
                                    "metadataFilters": "pdf",
                                    "limit": 5
                                }
                                """;

                // Act & Assert
                mockMvc.perform(post("/api/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                                .andExpect(status().isOk());

                // Verificar que o service recebeu os filtros corretos
                verify(chatService).chat(argThat(req -> "pdf".equals(req.getMetadataFilters())
                                && req.getUserId().equals(userId)
                                && "Teste com metadados".equals(req.getQuestion())));

                System.out.println("MetadataFilters aceitos corretamente");
        }

        @Test
        @DisplayName("POST /api/chat - Deve validar estrutura dos chunks retornados")
        void testChat_validateChunkStructure() throws Exception {
                // Arrange
                ChatRequestDto request = ChatRequestDto.builder()
                                .userId(TEST_USER_ID)
                                .question("Validar estrutura")
                                .build();

                RetrievedChunkDto chunk = RetrievedChunkDto.builder()
                                .id(TEST_CHUNK_ID)
                                .documentId(TEST_DOCUMENT_ID)
                                .contentSnippet("Este é o snippet de teste")
                                .chunkIndex(0)
                                .tokenCount(75)
                                .build();

                ChatResponseDto mockResponse = ChatResponseDto.builder()
                                .answer("Resposta de teste")
                                .chunks(List.of(chunk))
                                .build();

                when(chatService.chat(any(ChatRequestDto.class))).thenReturn(mockResponse);

                // Act & Assert
                mockMvc.perform(post("/api/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.chunks[0].id").value(TEST_CHUNK_ID.toString()))
                                .andExpect(jsonPath("$.chunks[0].documentId").value(TEST_DOCUMENT_ID.toString()))
                                .andExpect(jsonPath("$.chunks[0].contentSnippet").value("Este é o snippet de teste"))
                                .andExpect(jsonPath("$.chunks[0].chunkIndex").value(0))
                                .andExpect(jsonPath("$.chunks[0].tokenCount").value(75));

                System.out.println("Estrutura dos chunks validada corretamente");
        }
}
