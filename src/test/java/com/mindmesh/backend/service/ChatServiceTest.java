package com.mindmesh.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mindmesh.backend.dto.ChatRequestDto;
import com.mindmesh.backend.dto.ChatResponseDto;
import com.mindmesh.backend.dto.ChunkSearchResult;
import com.mindmesh.backend.dto.RetrievedChunkDto;
import com.mindmesh.backend.model.ChatMessage;
import com.mindmesh.backend.model.ChatSession;
import com.mindmesh.backend.repository.ChatMessageRepository;
import com.mindmesh.backend.repository.ChatSessionRepository;
import com.mindmesh.backend.repository.DocumentChunkRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para ChatService.
 * Alinhado com schema final (message, sessionId, usedChunkIds).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatServiceTest {

        private static final float[] MOCK_EMBEDDING = new float[] { 0.1f, 0.2f, 0.3f };
        private static final String MOCK_ANSWER = "Resposta de teste gerada pelo modelo.";
        private static final UUID TEST_USER_ID = UUID.randomUUID();
        private static final UUID TEST_DOCUMENT_ID = UUID.randomUUID();
        private static final UUID TEST_SESSION_ID = UUID.randomUUID();

        @Mock
        private EmbeddingService embeddingService;

        @Mock
        private DocumentChunkRepository documentChunkRepository;

        @Mock
        private ChatSessionRepository chatSessionRepository;

        @Mock
        private ChatMessageRepository chatMessageRepository;

        @Mock
        private ChatLanguageModel chatModel;

        private ChatService chatService;
        private ObjectMapper objectMapper = new ObjectMapper();

        @BeforeEach
        void setUp() throws Exception {
                when(embeddingService.embed(anyString())).thenReturn(MOCK_EMBEDDING);
                when(chatModel.generate(anyString())).thenReturn(MOCK_ANSWER);

                // Mock session creation
                ChatSession mockSession = ChatSession.builder()
                                .id(TEST_SESSION_ID)
                                .userId(TEST_USER_ID)
                                .title("Nova conversa")
                                .build();
                when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(mockSession);
                when(chatSessionRepository.findById(TEST_SESSION_ID)).thenReturn(Optional.of(mockSession));

                // Mock message save
                when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
                        ChatMessage msg = inv.getArgument(0);
                        if (msg.getId() == null) {
                                msg.setId(UUID.randomUUID());
                        }
                        return msg;
                });

                chatService = createChatServiceWithMocks();
        }

        private ChatService createChatServiceWithMocks() throws Exception {
                ChatService service = org.objenesis.ObjenesisHelper.newInstance(ChatService.class);

                setField(service, "embeddingService", embeddingService);
                setField(service, "documentChunkRepository", documentChunkRepository);
                setField(service, "chatSessionRepository", chatSessionRepository);
                setField(service, "chatMessageRepository", chatMessageRepository);
                setField(service, "chatModel", chatModel);
                setField(service, "mockMode", false);

                return service;
        }

        private void setField(Object target, String fieldName, Object value) throws Exception {
                Field field = ChatService.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
        }

        private List<ChunkSearchResult> createMockChunks(int count) {
                List<ChunkSearchResult> chunks = new ArrayList<>();

                for (int i = 0; i < count; i++) {
                        ChunkSearchResult chunk = ChunkSearchResult.builder()
                                        .id(UUID.randomUUID())
                                        .documentId(TEST_DOCUMENT_ID)
                                        .content("Conteúdo do chunk " + i)
                                        .chunkIndex(i)
                                        .tokenCount(50)
                                        .metadata("{\"chunk_index\": " + i + "}")
                                        .build();

                        chunks.add(chunk);
                }

                return chunks;
        }

        @Test
        @DisplayName("Deve retornar resposta com chunks e sessionId")
        void testChatServiceReturnsExpectedResponse() {
                List<ChunkSearchResult> mockChunks = createMockChunks(3);
                when(documentChunkRepository.findSimilar(any(), any(), any(), anyInt())).thenReturn(mockChunks);

                ChatRequestDto request = ChatRequestDto.builder()
                                .userId(TEST_USER_ID)
                                .message("Qual é o conteúdo dos documentos?")
                                .limit(5)
                                .build();

                ChatResponseDto response = chatService.chat(request);

                assertNotNull(response);
                assertEquals(MOCK_ANSWER, response.getAnswer());
                assertNotNull(response.getSessionId());
                assertNotNull(response.getChunks());
                assertEquals(3, response.getChunks().size());
                assertNotNull(response.getMessageId());

                verify(embeddingService).embed(request.getMessage());
                verify(documentChunkRepository).findSimilar(eq(MOCK_EMBEDDING), eq(TEST_USER_ID), isNull(), eq(5));
        }

        @Test
        @DisplayName("Deve lançar exceção para mensagem vazia")
        void testChatServiceThrowsExceptionForEmptyMessage() {
                ChatRequestDto request = ChatRequestDto.builder()
                                .userId(TEST_USER_ID)
                                .message("")
                                .build();

                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> chatService.chat(request));

                assertTrue(exception.getMessage().contains("mensagem"));
        }

        @Test
        @DisplayName("Deve lançar exceção para mensagem nula")
        void testChatServiceThrowsExceptionForNullMessage() {
                ChatRequestDto request = ChatRequestDto.builder()
                                .userId(TEST_USER_ID)
                                .message(null)
                                .build();

                assertThrows(IllegalArgumentException.class, () -> chatService.chat(request));
        }

        @Test
        @DisplayName("Deve lançar exceção para userId nulo")
        void testChatServiceThrowsExceptionForNullUserId() {
                ChatRequestDto request = ChatRequestDto.builder()
                                .userId(null)
                                .message("Qual é o conteúdo?")
                                .build();

                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> chatService.chat(request));

                assertTrue(exception.getMessage().contains("userId"));
        }

        @Test
        @DisplayName("Deve retornar mensagem padrão quando não há chunks")
        void testChatServiceReturnsDefaultMessageWhenNoChunks() {
                when(documentChunkRepository.findSimilar(any(), any(), any(), anyInt()))
                                .thenReturn(List.of());

                ChatRequestDto request = ChatRequestDto.builder()
                                .userId(TEST_USER_ID)
                                .message("Pergunta sobre algo inexistente")
                                .build();

                ChatResponseDto response = chatService.chat(request);

                assertNotNull(response);
                assertTrue(response.getAnswer().contains("Não encontrei"));
                assertTrue(response.getChunks().isEmpty());

                verify(chatModel, never()).generate(anyString());
        }

        @Test
        @DisplayName("Deve usar limit padrão quando não especificado")
        void testChatServiceUsesDefaultLimit() {
                List<ChunkSearchResult> mockChunks = createMockChunks(2);
                when(documentChunkRepository.findSimilar(any(), any(), any(), anyInt())).thenReturn(mockChunks);

                ChatRequestDto request = ChatRequestDto.builder()
                                .userId(TEST_USER_ID)
                                .message("Pergunta teste")
                                .limit(null)
                                .build();

                chatService.chat(request);

                verify(documentChunkRepository).findSimilar(any(), any(), any(), eq(5)); // Default is 5
        }

        @Test
        @DisplayName("Deve passar metadataFilters para o repository")
        void testChatServicePassesMetadataFilters() {
                String metadataFilter = "{\"source\": \"pdf\"}";
                List<ChunkSearchResult> mockChunks = createMockChunks(1);
                when(documentChunkRepository.findSimilar(any(), any(), any(), anyInt())).thenReturn(mockChunks);

                ChatRequestDto request = ChatRequestDto.builder()
                                .userId(TEST_USER_ID)
                                .message("Pergunta teste")
                                .metadataFilters(metadataFilter)
                                .limit(5)
                                .build();

                chatService.chat(request);

                verify(documentChunkRepository).findSimilar(any(), eq(TEST_USER_ID), eq(metadataFilter), eq(5));
        }

        @Test
        @DisplayName("Deve truncar snippets longos")
        void testChatServiceTruncatesLongSnippets() {
                String longContent = "A".repeat(500);

                ChunkSearchResult longChunk = ChunkSearchResult.builder()
                                .id(UUID.randomUUID())
                                .documentId(TEST_DOCUMENT_ID)
                                .content(longContent)
                                .chunkIndex(0)
                                .tokenCount(100)
                                .metadata("{\"chunk_index\": 0}")
                                .build();

                when(documentChunkRepository.findSimilar(any(), any(), any(), anyInt()))
                                .thenReturn(List.of(longChunk));

                ChatRequestDto request = ChatRequestDto.builder()
                                .userId(TEST_USER_ID)
                                .message("Pergunta teste")
                                .build();

                ChatResponseDto response = chatService.chat(request);

                String snippet = response.getChunks().get(0).getContentSnippet();
                assertTrue(snippet.length() <= 300);
                assertTrue(snippet.endsWith("..."));
        }
}
