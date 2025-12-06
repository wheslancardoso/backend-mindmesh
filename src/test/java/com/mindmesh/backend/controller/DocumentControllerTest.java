package com.mindmesh.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindmesh.backend.model.Document;
import com.mindmesh.backend.repository.DocumentChunkRepository;
import com.mindmesh.backend.repository.DocumentRepository;
import com.mindmesh.backend.service.DocumentIngestionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes do DocumentController usando MockMvc.
 * Testa apenas a camada web sem subir contexto completo.
 */
@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DocumentIngestionService documentIngestionService;

    @MockBean
    private DocumentRepository documentRepository;

    @MockBean
    private DocumentChunkRepository documentChunkRepository;

    private static final UUID TEST_USER_ID = UUID.randomUUID();
    private static final UUID TEST_DOCUMENT_ID = UUID.randomUUID();

    /**
     * Cria um Document simulado para testes.
     */
    private Document createMockDocument(UUID id, UUID userId, String filename) {
        return Document.builder()
                .id(id)
                .userId(userId)
                .filename(filename)
                .contentType("text/plain")
                .sizeBytes(1024L)
                .text("Conteúdo de teste do documento")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("POST /upload - Deve fazer upload com sucesso")
    void testUploadDocument_success() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.txt",
                "text/plain",
                "Conteúdo de teste para upload".getBytes());

        when(documentIngestionService.ingestDocument(any(), anyString(), anyString(), any()))
                .thenReturn(TEST_DOCUMENT_ID);

        // Act & Assert
        mockMvc.perform(multipart("/api/documents/upload")
                .file(file)
                .param("userId", TEST_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.documentId").value(TEST_DOCUMENT_ID.toString()))
                .andExpect(jsonPath("$.filename").value("sample.txt"))
                .andExpect(jsonPath("$.message").value("Documento processado com sucesso"))
                .andExpect(jsonPath("$.size").value(greaterThan(0)));

        // Verificar que o service foi chamado
        verify(documentIngestionService).ingestDocument(
                eq(TEST_USER_ID),
                eq("sample.txt"),
                eq("text/plain"),
                any());

        System.out.println("Upload com sucesso - documentId: " + TEST_DOCUMENT_ID);
    }

    @Test
    @DisplayName("POST /upload - Deve retornar 400 para arquivo vazio")
    void testUploadDocument_missingFile() throws Exception {
        // Arrange
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.txt",
                "text/plain",
                new byte[0]);

        // Act & Assert
        mockMvc.perform(multipart("/api/documents/upload")
                .file(emptyFile)
                .param("userId", TEST_USER_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists());

        // Verificar que o service NÃO foi chamado
        verify(documentIngestionService, never()).ingestDocument(any(), any(), any(), any());

        System.out.println("Arquivo vazio rejeitado corretamente");
    }

    @Test
    @DisplayName("GET /list - Deve listar documentos do usuário")
    void testListDocuments_success() throws Exception {
        // Arrange
        Document doc1 = createMockDocument(UUID.randomUUID(), TEST_USER_ID, "doc1.txt");
        Document doc2 = createMockDocument(UUID.randomUUID(), TEST_USER_ID, "doc2.pdf");

        when(documentRepository.findByUserIdOrderByCreatedAtDesc(TEST_USER_ID))
                .thenReturn(List.of(doc1, doc2));

        // Act & Assert
        mockMvc.perform(get("/api/documents/list")
                .param("userId", TEST_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].filename").value("doc1.txt"))
                .andExpect(jsonPath("$[1].filename").value("doc2.pdf"));

        verify(documentRepository).findByUserIdOrderByCreatedAtDesc(TEST_USER_ID);

        System.out.println("Listagem retornou 2 documentos");
    }

    @Test
    @DisplayName("GET /list - Deve retornar lista vazia se não houver documentos")
    void testListDocuments_empty() throws Exception {
        // Arrange
        when(documentRepository.findByUserIdOrderByCreatedAtDesc(TEST_USER_ID))
                .thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/api/documents/list")
                .param("userId", TEST_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        System.out.println("Lista vazia retornada corretamente");
    }

    @Test
    @DisplayName("GET /{id} - Deve retornar documento por ID")
    void testGetDocumentById_success() throws Exception {
        // Arrange
        Document document = createMockDocument(TEST_DOCUMENT_ID, TEST_USER_ID, "test.txt");

        when(documentRepository.findById(TEST_DOCUMENT_ID))
                .thenReturn(Optional.of(document));

        // Act & Assert
        mockMvc.perform(get("/api/documents/{documentId}", TEST_DOCUMENT_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(TEST_DOCUMENT_ID.toString()))
                .andExpect(jsonPath("$.filename").value("test.txt"))
                .andExpect(jsonPath("$.contentType").value("text/plain"))
                .andExpect(jsonPath("$.text").value("Conteúdo de teste do documento"));

        verify(documentRepository).findById(TEST_DOCUMENT_ID);

        System.out.println("Documento retornado por ID");
    }

    @Test
    @DisplayName("GET /{id} - Deve retornar 400 para documento não encontrado")
    void testGetDocumentById_notFound() throws Exception {
        // Arrange
        UUID fakeId = UUID.randomUUID();
        when(documentRepository.findById(fakeId))
                .thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/documents/{documentId}", fakeId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").value(containsString("não encontrado")));

        System.out.println("Documento não encontrado retornou 400");
    }

    @Test
    @DisplayName("DELETE /{id} - Deve deletar documento com sucesso")
    void testDeleteDocument_success() throws Exception {
        // Arrange
        when(documentRepository.existsById(TEST_DOCUMENT_ID)).thenReturn(true);

        // Act & Assert
        mockMvc.perform(delete("/api/documents/{documentId}", TEST_DOCUMENT_ID))
                .andExpect(status().isNoContent());

        // Verificar chamadas
        verify(documentChunkRepository).deleteByDocumentId(TEST_DOCUMENT_ID);
        verify(documentRepository).deleteById(TEST_DOCUMENT_ID);

        System.out.println("Documento deletado com sucesso");
    }

    @Test
    @DisplayName("DELETE /{id} - Deve retornar 400 para documento inexistente")
    void testDeleteDocument_notFound() throws Exception {
        // Arrange
        UUID fakeId = UUID.randomUUID();
        when(documentRepository.existsById(fakeId)).thenReturn(false);

        // Act & Assert
        mockMvc.perform(delete("/api/documents/{documentId}", fakeId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("não encontrado")));

        // Verificar que delete NÃO foi chamado
        verify(documentChunkRepository, never()).deleteByDocumentId(any());
        verify(documentRepository, never()).deleteById(any());

        System.out.println("Delete de documento inexistente rejeitado");
    }

    @Test
    @DisplayName("POST /{id}/reprocess - Deve reprocessar documento com sucesso")
    void testReprocessDocument_success() throws Exception {
        // Arrange
        Document document = createMockDocument(TEST_DOCUMENT_ID, TEST_USER_ID, "reprocess.txt");

        when(documentIngestionService.reprocessDocument(TEST_DOCUMENT_ID)).thenReturn(5);
        when(documentRepository.findById(TEST_DOCUMENT_ID)).thenReturn(Optional.of(document));

        // Act & Assert
        mockMvc.perform(post("/api/documents/{documentId}/reprocess", TEST_DOCUMENT_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.documentId").value(TEST_DOCUMENT_ID.toString()))
                .andExpect(jsonPath("$.chunksGenerated").value(5))
                .andExpect(jsonPath("$.filename").value("reprocess.txt"))
                .andExpect(jsonPath("$.message").value("Documento reprocessado com sucesso"));

        verify(documentIngestionService).reprocessDocument(TEST_DOCUMENT_ID);

        System.out.println("Reprocessamento retornou 5 chunks");
    }

    @Test
    @DisplayName("POST /{id}/reprocess - Deve retornar erro para documento inexistente")
    void testReprocessDocument_notFound() throws Exception {
        // Arrange
        UUID fakeId = UUID.randomUUID();
        when(documentIngestionService.reprocessDocument(fakeId))
                .thenThrow(new RuntimeException("Documento não encontrado: " + fakeId));

        // Act & Assert
        mockMvc.perform(post("/api/documents/{documentId}/reprocess", fakeId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists());

        System.out.println("Reprocessamento de documento inexistente rejeitado");
    }
}
