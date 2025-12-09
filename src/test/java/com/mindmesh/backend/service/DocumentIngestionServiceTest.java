package com.mindmesh.backend.service;

import com.mindmesh.backend.dto.IngestionResult;
import com.mindmesh.backend.model.Document;
import com.mindmesh.backend.model.DocumentChunk;
import com.mindmesh.backend.repository.DocumentChunkRepository;
import com.mindmesh.backend.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Testes de integração para DocumentIngestionService.
 * Alinhado com schema final (fileHash, status, sem contentType/text/sizeBytes).
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class DocumentIngestionServiceTest {

        private static final float[] MOCK_EMBEDDING = new float[] { 0.1f, 0.2f, 0.3f };

        @MockBean
        private EmbeddingService embeddingService;

        @Autowired
        private DocumentIngestionService documentIngestionService;

        @Autowired
        private DocumentRepository documentRepository;

        @Autowired
        private DocumentChunkRepository documentChunkRepository;

        @BeforeEach
        void setUp() {
                when(embeddingService.embed(anyString())).thenReturn(MOCK_EMBEDDING);
        }

        @Test
        @DisplayName("Deve criar documento e chunks ao ingerir arquivo")
        void testIngestDocumentCreatesDocumentAndChunks() throws IOException {
                // Arrange
                UUID userId = UUID.randomUUID();
                byte[] bytes = loadResource("sample.txt");
                String filename = "teste.txt";
                String contentType = "text/plain";

                // Act
                IngestionResult result = documentIngestionService.ingestDocument(userId, filename, bytes, contentType);
                Document document = result.document();

                // Assert - Resultado não é duplicado
                assertFalse(result.isDuplicate(), "Primeiro upload não deve ser duplicado");

                // Assert - Documento foi criado
                assertNotNull(document, "Documento não deve ser nulo");
                assertNotNull(document.getId(), "DocumentId não deve ser nulo");

                Optional<Document> documentOpt = documentRepository.findById(document.getId());
                assertTrue(documentOpt.isPresent(), "Documento deve existir no banco");

                Document saved = documentOpt.get();
                assertEquals(userId, saved.getUserId(), "UserId deve corresponder");
                assertEquals(filename, saved.getFilename(), "Filename deve corresponder");
                assertNotNull(saved.getFileHash(), "FileHash não deve ser nulo");
                assertEquals(64, saved.getFileHash().length(), "FileHash deve ter 64 chars (SHA-256)");
                assertEquals("completed", saved.getStatus(), "Status deve ser 'completed'");
                assertNotNull(saved.getCreatedAt(), "CreatedAt deve estar preenchido");

                // Assert - Chunks foram criados
                List<DocumentChunk> chunks = documentChunkRepository
                                .findByDocumentIdOrderByChunkIndexAsc(document.getId());
                assertFalse(chunks.isEmpty(), "Chunks devem ser criados");

                for (int i = 0; i < chunks.size(); i++) {
                        DocumentChunk chunk = chunks.get(i);
                        assertEquals(document.getId(), chunk.getDocumentId(), "DocumentId do chunk deve corresponder");
                        assertEquals(i, chunk.getChunkIndex(), "ChunkIndex deve ser sequencial");
                        assertNotNull(chunk.getContent(), "Conteúdo do chunk não deve ser nulo");
                        assertTrue(chunk.getTokenCount() > 0, "TokenCount deve ser maior que 0");
                }
        }

        @Test
        @DisplayName("Deve lançar exceção ao ingerir arquivo vazio")
        void testIngestDocumentWithEmptyFile() {
                UUID userId = UUID.randomUUID();
                byte[] emptyBytes = new byte[0];

                RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                        documentIngestionService.ingestDocument(userId, "vazio.txt", emptyBytes, "text/plain");
                });

                assertTrue(exception.getMessage().contains("Falha") ||
                                exception.getMessage().contains("extrair") ||
                                exception.getMessage().contains("texto"),
                                "Mensagem deve indicar problema na extração");
        }

        @Test
        @DisplayName("Deve detectar duplicata por fileHash")
        void testIngestDocumentDetectsDuplicate() throws IOException {
                UUID userId = UUID.randomUUID();
                byte[] bytes = loadResource("sample.txt");

                // Primeira ingestão
                IngestionResult firstResult = documentIngestionService.ingestDocument(userId, "original.txt", bytes,
                                "text/plain");

                // Segunda ingestão com mesmo conteúdo
                IngestionResult secondResult = documentIngestionService.ingestDocument(userId, "copia.txt", bytes,
                                "text/plain");

                // Assert - primeiro não é duplicado, segundo é
                assertFalse(firstResult.isDuplicate(), "Primeiro upload não deve ser duplicado");
                assertTrue(secondResult.isDuplicate(), "Segundo upload deve ser duplicado");

                // Assert - deve retornar o mesmo documento
                assertEquals(firstResult.document().getId(), secondResult.document().getId(),
                                "Deve retornar documento existente");
                assertEquals(firstResult.document().getFileHash(), secondResult.document().getFileHash(),
                                "FileHash deve ser igual");
        }

        @Test
        @DisplayName("Deve remover documento e chunks ao deletar")
        void testDeleteDocumentRemovesChunksAlso() throws IOException {
                UUID userId = UUID.randomUUID();
                byte[] bytes = loadResource("sample.txt");
                IngestionResult result = documentIngestionService.ingestDocument(userId, "para_deletar.txt", bytes,
                                "text/plain");
                Document document = result.document();

                assertTrue(documentRepository.existsById(document.getId()), "Documento deve existir antes da deleção");

                List<DocumentChunk> chunksAntes = documentChunkRepository
                                .findByDocumentIdOrderByChunkIndexAsc(document.getId());
                assertFalse(chunksAntes.isEmpty(), "Chunks devem existir antes da deleção");

                // Act
                documentIngestionService.deleteDocument(document.getId());

                // Assert
                assertFalse(documentRepository.existsById(document.getId()), "Documento não deve existir após deleção");

                List<DocumentChunk> chunksDepois = documentChunkRepository
                                .findByDocumentIdOrderByChunkIndexAsc(document.getId());
                assertTrue(chunksDepois.isEmpty(), "Chunks não devem existir após deleção");
        }

        @Test
        @DisplayName("Deve contar chunks corretamente")
        void testGetChunkCount() throws IOException {
                UUID userId = UUID.randomUUID();
                byte[] bytes = loadResource("sample.txt");
                IngestionResult result = documentIngestionService.ingestDocument(userId, "contar.txt", bytes,
                                "text/plain");
                Document document = result.document();

                int count = documentIngestionService.getChunkCount(document.getId());

                assertTrue(count > 0, "Deve haver pelo menos 1 chunk");
        }

        private byte[] loadResource(String filename) throws IOException {
                try (InputStream is = getClass().getClassLoader().getResourceAsStream(filename)) {
                        if (is == null) {
                                throw new IOException("Recurso não encontrado: " + filename);
                        }
                        return is.readAllBytes();
                }
        }
}
