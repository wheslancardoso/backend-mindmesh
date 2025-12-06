package com.mindmesh.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mindmesh.backend.dto.ChatRequestDto;
import com.mindmesh.backend.dto.ChatResponseDto;
import com.mindmesh.backend.dto.RetrievedChunkDto;
import com.mindmesh.backend.model.DocumentChunk;
import com.mindmesh.backend.repository.DocumentChunkRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para ChatService.
 * Usa mocks para EmbeddingService, DocumentChunkRepository e ChatLanguageModel.
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final float[] MOCK_EMBEDDING = new float[] { 0.1f, 0.2f, 0.3f };
    private static final String MOCK_ANSWER = "Esta é uma resposta de teste gerada pelo modelo mockado.";
    private static final UUID TEST_USER_ID = UUID.randomUUID();
    private static final UUID TEST_DOCUMENT_ID = UUID.randomUUID();

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private DocumentChunkRepository documentChunkRepository;

    @Mock
    private ChatLanguageModel chatModel;

    private ChatService chatService;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        // Configurar mocks
        when(embeddingService.embed(anyString())).thenReturn(MOCK_EMBEDDING);
        when(chatModel.generate(anyString())).thenReturn(MOCK_ANSWER);

        // Criar instância do ChatService com construtor customizado para testes
        chatService = createChatServiceWithMocks();
    }

    /**
     * Cria instância do ChatService injetando mocks via reflection.
     */
    private ChatService createChatServiceWithMocks() throws Exception {
        // Criar instância usando construtor com API key fake
        ChatService service = new ChatService(
                embeddingService,
                documentChunkRepository,
                "test-api-key");

        // Substituir o chatModel via reflection
        Field chatModelField = ChatService.class.getDeclaredField("chatModel");
        chatModelField.setAccessible(true);
        chatModelField.set(service, chatModel);

        return service;
    }

    /**
     * Cria lista de DocumentChunks mockados.
     */
    private List<DocumentChunk> createMockChunks(int count) {
        List<DocumentChunk> chunks = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            ObjectNode metadata = objectMapper.createObjectNode();
            metadata.put("chunk_index", i);

            DocumentChunk chunk = DocumentChunk.builder()
                    .id(UUID.randomUUID())
                    .documentId(TEST_DOCUMENT_ID)
                    .content("Conteúdo do chunk " + i + ". Este é um texto de exemplo para teste.")
                    .embedding(MOCK_EMBEDDING)
                    .chunkIndex(i)
                    .tokenCount(50)
                    .metadata(metadata)
                    .build();

            chunks.add(chunk);
        }

        return chunks;
    }

    @Test
    @DisplayName("Deve retornar resposta esperada com chunks")
    void testChatServiceReturnsExpectedResponse() {
        // Arrange
        List<DocumentChunk> mockChunks = createMockChunks(3);
        when(documentChunkRepository.findSimilar(any(), any(), any(), anyInt()))
                .thenReturn(mockChunks);

        ChatRequestDto request = ChatRequestDto.builder()
                .userId(TEST_USER_ID)
                .question("Qual é o conteúdo dos documentos?")
                .metadataFilters(null)
                .limit(5)
                .build();

        System.out.println("Testando chat com pergunta: " + request.getQuestion());

        // Act
        ChatResponseDto response = chatService.chat(request);

        // Assert
        assertNotNull(response, "Resposta não deve ser nula");
        assertEquals(MOCK_ANSWER, response.getAnswer(), "Answer deve ser a resposta mockada");
        assertNotNull(response.getChunks(), "Lista de chunks não deve ser nula");
        assertEquals(3, response.getChunks().size(), "Deve retornar 3 chunks");

        // Validar snippets dos chunks
        for (int i = 0; i < response.getChunks().size(); i++) {
            RetrievedChunkDto chunk = response.getChunks().get(i);
            assertNotNull(chunk.getId(), "ID do chunk não deve ser nulo");
            assertNotNull(chunk.getContentSnippet(), "Snippet não deve ser nulo");
            assertEquals(i, chunk.getChunkIndex(), "ChunkIndex deve corresponder");
            assertEquals(TEST_DOCUMENT_ID, chunk.getDocumentId(), "DocumentId deve corresponder");
        }

        // Verificar que os mocks foram chamados
        verify(embeddingService).embed(request.getQuestion());
        verify(documentChunkRepository).findSimilar(eq(MOCK_EMBEDDING), eq(TEST_USER_ID), isNull(), eq(5));
        verify(chatModel).generate(anyString());

        System.out.println("Resposta recebida: " + response.getAnswer());
        System.out.println("Chunks retornados: " + response.getChunks().size());
    }

    @Test
    @DisplayName("Deve lançar exceção para pergunta vazia")
    void testChatServiceThrowsExceptionForEmptyQuestion() {
        // Arrange
        ChatRequestDto request = ChatRequestDto.builder()
                .userId(TEST_USER_ID)
                .question("")
                .build();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> chatService.chat(request));

        assertTrue(exception.getMessage().contains("pergunta"),
                "Mensagem deve mencionar 'pergunta'");

        System.out.println("Exceção esperada: " + exception.getMessage());
    }

    @Test
    @DisplayName("Deve lançar exceção para pergunta nula")
    void testChatServiceThrowsExceptionForNullQuestion() {
        // Arrange
        ChatRequestDto request = ChatRequestDto.builder()
                .userId(TEST_USER_ID)
                .question(null)
                .build();

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> chatService.chat(request));

        System.out.println("Exceção lançada corretamente para pergunta nula");
    }

    @Test
    @DisplayName("Deve lançar exceção para userId nulo")
    void testChatServiceThrowsExceptionForNullUserId() {
        // Arrange
        ChatRequestDto request = ChatRequestDto.builder()
                .userId(null)
                .question("Qual é o conteúdo?")
                .build();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> chatService.chat(request));

        assertTrue(exception.getMessage().contains("userId"),
                "Mensagem deve mencionar 'userId'");

        System.out.println("Exceção esperada: " + exception.getMessage());
    }

    @Test
    @DisplayName("Deve retornar mensagem padrão quando não há chunks")
    void testChatServiceReturnsDefaultMessageWhenNoChunks() {
        // Arrange
        when(documentChunkRepository.findSimilar(any(), any(), any(), anyInt()))
                .thenReturn(List.of()); // Lista vazia

        ChatRequestDto request = ChatRequestDto.builder()
                .userId(TEST_USER_ID)
                .question("Pergunta sobre algo que não existe")
                .build();

        // Act
        ChatResponseDto response = chatService.chat(request);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getAnswer());
        assertTrue(response.getAnswer().contains("Não encontrei"),
                "Resposta deve indicar que não encontrou documentos");
        assertTrue(response.getChunks().isEmpty(),
                "Lista de chunks deve estar vazia");

        // Verificar que o modelo de chat NÃO foi chamado
        verify(chatModel, never()).generate(anyString());

        System.out.println("Resposta para nenhum chunk: " + response.getAnswer());
    }

    @Test
    @DisplayName("Deve usar limit padrão quando não especificado")
    void testChatServiceUsesDefaultLimit() {
        // Arrange
        List<DocumentChunk> mockChunks = createMockChunks(2);
        when(documentChunkRepository.findSimilar(any(), any(), any(), anyInt()))
                .thenReturn(mockChunks);

        ChatRequestDto request = ChatRequestDto.builder()
                .userId(TEST_USER_ID)
                .question("Pergunta teste")
                .limit(null) // Não especificado
                .build();

        // Act
        chatService.chat(request);

        // Assert - verificar que usou limit padrão (8)
        verify(documentChunkRepository).findSimilar(any(), any(), any(), eq(8));

        System.out.println("Limit padrão 8 foi utilizado");
    }

    @Test
    @DisplayName("Deve passar metadataFilters para o repository")
    void testChatServicePassesMetadataFilters() {
        // Arrange
        String metadataFilter = "{\"source\": \"pdf\"}";
        List<DocumentChunk> mockChunks = createMockChunks(1);
        when(documentChunkRepository.findSimilar(any(), any(), any(), anyInt()))
                .thenReturn(mockChunks);

        ChatRequestDto request = ChatRequestDto.builder()
                .userId(TEST_USER_ID)
                .question("Pergunta teste")
                .metadataFilters(metadataFilter)
                .limit(5)
                .build();

        // Act
        chatService.chat(request);

        // Assert
        verify(documentChunkRepository).findSimilar(any(), eq(TEST_USER_ID), eq(metadataFilter), eq(5));

        System.out.println("MetadataFilters passados corretamente");
    }

    @Test
    @DisplayName("Deve truncar snippets longos")
    void testChatServiceTruncatesLongSnippets() {
        // Arrange
        String longContent = "A".repeat(500); // Conteúdo maior que 300 chars

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("chunk_index", 0);

        DocumentChunk longChunk = DocumentChunk.builder()
                .id(UUID.randomUUID())
                .documentId(TEST_DOCUMENT_ID)
                .content(longContent)
                .embedding(MOCK_EMBEDDING)
                .chunkIndex(0)
                .tokenCount(100)
                .metadata(metadata)
                .build();

        when(documentChunkRepository.findSimilar(any(), any(), any(), anyInt()))
                .thenReturn(List.of(longChunk));

        ChatRequestDto request = ChatRequestDto.builder()
                .userId(TEST_USER_ID)
                .question("Pergunta teste")
                .build();

        // Act
        ChatResponseDto response = chatService.chat(request);

        // Assert
        assertNotNull(response.getChunks());
        assertEquals(1, response.getChunks().size());

        String snippet = response.getChunks().get(0).getContentSnippet();
        assertTrue(snippet.length() <= 300,
                "Snippet deve ser truncado para no máximo 300 chars");
        assertTrue(snippet.endsWith("..."),
                "Snippet truncado deve terminar com ...");

        System.out.println("Snippet truncado: " + snippet.length() + " chars");
    }

    @Test
    @DisplayName("Deve incluir tokenCount no DTO de resposta")
    void testChatServiceIncludesTokenCount() {
        // Arrange
        List<DocumentChunk> mockChunks = createMockChunks(2);
        when(documentChunkRepository.findSimilar(any(), any(), any(), anyInt()))
                .thenReturn(mockChunks);

        ChatRequestDto request = ChatRequestDto.builder()
                .userId(TEST_USER_ID)
                .question("Pergunta teste")
                .build();

        // Act
        ChatResponseDto response = chatService.chat(request);

        // Assert
        for (RetrievedChunkDto chunk : response.getChunks()) {
            assertNotNull(chunk.getTokenCount(), "TokenCount não deve ser nulo");
            assertEquals(50, chunk.getTokenCount(), "TokenCount deve corresponder ao mock");
        }

        System.out.println("TokenCount incluído corretamente nos chunks");
    }
}
