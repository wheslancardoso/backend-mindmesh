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
 * Alinhado com schema final (sem contentType, sizeBytes, text).
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
         * Cria um Document simulado para testes (schema final).
         */
        private Document createMockDocument(UUID id, UUID userId, String filename) {
                return Document.builder()
                                .id(id)
                                .userId(userId)
                                .filename(filename)
                                .fileHash("abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234")
                                .status("completed")
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

                Document mockDocument = createMockDocument(TEST_DOCUMENT_ID, TEST_USER_ID, "sample.txt");

                when(documentIngestionService.ingestDocument(any(), anyString(), any(byte[].class), anyString()))
                                .thenReturn(mockDocument);
                when(documentIngestionService.getChunkCount(TEST_DOCUMENT_ID)).thenReturn(5);

                // Act & Assert
                mockMvc.perform(multipart("/api/documents/upload")
                                .file(file)
                                .param("userId", TEST_USER_ID.toString()))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                                .andExpect(jsonPath("$.documentId").value(TEST_DOCUMENT_ID.toString()))
                                .andExpect(jsonPath("$.filename").value("sample.txt"))
                                .andExpect(jsonPath("$.fileHash").exists())
                                .andExpect(jsonPath("$.status").value("completed"))
                                .andExpect(jsonPath("$.message").value("Documento processado com sucesso"));

                verify(documentIngestionService).ingestDocument(
                                eq(TEST_USER_ID),
                                eq("sample.txt"),
                                any(byte[].class),
                                eq("text/plain"));
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
                                .andExpect(jsonPath("$.error").exists());

                verify(documentIngestionService, never()).ingestDocument(any(), any(), any(), any());
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
                                .andExpect(jsonPath("$", hasSize(2)))
                                .andExpect(jsonPath("$[0].filename").value("doc1.txt"))
                                .andExpect(jsonPath("$[1].filename").value("doc2.pdf"));

                verify(documentRepository).findByUserIdOrderByCreatedAtDesc(TEST_USER_ID);
        }

        @Test
        @DisplayName("GET /list - Deve retornar lista vazia se não houver documentos")
        void testListDocuments_empty() throws Exception {
                when(documentRepository.findByUserIdOrderByCreatedAtDesc(TEST_USER_ID))
                                .thenReturn(List.of());

                mockMvc.perform(get("/api/documents/list")
                                .param("userId", TEST_USER_ID.toString()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("GET /{id} - Deve retornar documento por ID")
        void testGetDocumentById_success() throws Exception {
                Document document = createMockDocument(TEST_DOCUMENT_ID, TEST_USER_ID, "test.txt");

                when(documentRepository.findById(TEST_DOCUMENT_ID))
                                .thenReturn(Optional.of(document));

                mockMvc.perform(get("/api/documents/{documentId}", TEST_DOCUMENT_ID))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(TEST_DOCUMENT_ID.toString()))
                                .andExpect(jsonPath("$.filename").value("test.txt"))
                                .andExpect(jsonPath("$.fileHash").exists())
                                .andExpect(jsonPath("$.status").value("completed"));

                verify(documentRepository).findById(TEST_DOCUMENT_ID);
        }

        @Test
        @DisplayName("GET /{id} - Deve retornar 400 para documento não encontrado")
        void testGetDocumentById_notFound() throws Exception {
                UUID fakeId = UUID.randomUUID();
                when(documentRepository.findById(fakeId))
                                .thenReturn(Optional.empty());

                mockMvc.perform(get("/api/documents/{documentId}", fakeId))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        @DisplayName("DELETE /{id} - Deve deletar documento com sucesso")
        void testDeleteDocument_success() throws Exception {
                when(documentRepository.existsById(TEST_DOCUMENT_ID)).thenReturn(true);

                mockMvc.perform(delete("/api/documents/{documentId}", TEST_DOCUMENT_ID))
                                .andExpect(status().isNoContent());

                verify(documentIngestionService).deleteDocument(TEST_DOCUMENT_ID);
        }

        @Test
        @DisplayName("DELETE /{id} - Deve retornar 400 para documento inexistente")
        void testDeleteDocument_notFound() throws Exception {
                UUID fakeId = UUID.randomUUID();
                when(documentRepository.existsById(fakeId)).thenReturn(false);

                mockMvc.perform(delete("/api/documents/{documentId}", fakeId))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(containsString("não encontrado")));

                verify(documentIngestionService, never()).deleteDocument(any());
        }
}
