package com.mindmesh.backend.service;

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
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
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
 * Usa contexto Spring completo com H2 em memória.
 */
@SpringBootTest
@EntityScan(basePackages = "com.mindmesh.backend.model")
@EnableJpaRepositories(basePackages = "com.mindmesh.backend.repository")
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
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
                // Configurar mock do EmbeddingService para evitar chamadas reais à OpenAI
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

                System.out.println("Iniciando ingestão - userId: " + userId);
                System.out.println("Tamanho do arquivo: " + bytes.length + " bytes");

                // Act
                UUID documentId = documentIngestionService.ingestDocument(userId, filename, contentType, bytes);

                // Assert - Documento foi criado
                assertNotNull(documentId, "DocumentId não deve ser nulo");

                Optional<Document> documentOpt = documentRepository.findById(documentId);
                assertTrue(documentOpt.isPresent(), "Documento deve existir no banco");

                Document document = documentOpt.get();
                assertEquals(userId, document.getUserId(), "UserId deve corresponder");
                assertEquals(filename, document.getFilename(), "Filename deve corresponder");
                assertEquals(contentType, document.getContentType(), "ContentType deve corresponder");
                assertNotNull(document.getText(), "Texto extraído não deve ser nulo");
                assertTrue(document.getText().contains("exemplo"), "Texto deve conter palavra 'exemplo'");
                assertNotNull(document.getCreatedAt(), "CreatedAt deve estar preenchido");

                System.out.println("Documento criado: " + documentId);
                System.out.println("Texto extraído: " + document.getText().length() + " chars");

                // Assert - Chunks foram criados
                List<DocumentChunk> chunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
                assertFalse(chunks.isEmpty(), "Chunks devem ser criados");

                System.out.println("Chunks criados: " + chunks.size());

                // Validar cada chunk
                for (int i = 0; i < chunks.size(); i++) {
                        DocumentChunk chunk = chunks.get(i);

                        assertEquals(documentId, chunk.getDocumentId(),
                                        "DocumentId do chunk deve corresponder");

                        assertEquals(i, chunk.getChunkIndex(),
                                        "ChunkIndex deve ser sequencial começando em 0");

                        assertArrayEquals(MOCK_EMBEDDING, chunk.getEmbedding(),
                                        "Embedding deve ser o valor mockado");

                        assertNotNull(chunk.getContent(),
                                        "Conteúdo do chunk não deve ser nulo");

                        assertTrue(chunk.getTokenCount() > 0,
                                        "TokenCount deve ser maior que 0");

                        assertNotNull(chunk.getMetadata(),
                                        "Metadata não deve ser nula");

                        assertTrue(chunk.getMetadata().has("chunk_index"),
                                        "Metadata deve conter 'chunk_index'");

                        assertEquals(i, chunk.getMetadata().get("chunk_index").asInt(),
                                        "chunk_index na metadata deve corresponder ao índice");

                        System.out.printf("  Chunk %d: %d chars, %d tokens%n",
                                        i, chunk.getContent().length(), chunk.getTokenCount());
                }
        }

        @Test
        @DisplayName("Deve lançar exceção ao ingerir arquivo vazio")
        void testIngestDocumentWithEmptyFile() {
                // Arrange
                UUID userId = UUID.randomUUID();
                byte[] emptyBytes = new byte[0];

                // Act & Assert
                RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                        documentIngestionService.ingestDocument(userId, "vazio.txt", "text/plain", emptyBytes);
                });

                System.out.println("Exceção esperada: " + exception.getMessage());
                assertTrue(exception.getMessage().contains("Falha") ||
                                exception.getMessage().contains("extrair") ||
                                exception.getMessage().contains("texto"),
                                "Mensagem deve indicar problema na extração");
        }

        @Test
        @DisplayName("Deve regenerar chunks ao reprocessar documento")
        void testReprocessDocumentRegeneratesChunks() throws IOException {
                // Arrange - criar documento inicial
                UUID userId = UUID.randomUUID();
                byte[] bytes = loadResource("sample.txt");
                UUID documentId = documentIngestionService.ingestDocument(userId, "original.txt", "text/plain", bytes);

                // Obter chunks originais
                List<DocumentChunk> originalChunks = documentChunkRepository
                                .findByDocumentIdOrderByChunkIndexAsc(documentId);
                int originalChunkCount = originalChunks.size();
                UUID firstOriginalChunkId = originalChunks.get(0).getId();

                System.out.println("Chunks originais: " + originalChunkCount);
                System.out.println("Primeiro chunk ID original: " + firstOriginalChunkId);

                // Act - reprocessar
                int newChunkCount = documentIngestionService.reprocessDocument(documentId);

                // Assert
                assertEquals(originalChunkCount, newChunkCount,
                                "Número de chunks deve ser similar (mesmo texto)");

                // Verificar que chunks antigos foram removidos e novos foram criados
                List<DocumentChunk> newChunks = documentChunkRepository
                                .findByDocumentIdOrderByChunkIndexAsc(documentId);
                assertFalse(newChunks.isEmpty(), "Novos chunks devem existir");

                // Verificar que os IDs são diferentes (chunks foram regenerados)
                UUID firstNewChunkId = newChunks.get(0).getId();
                assertNotEquals(firstOriginalChunkId, firstNewChunkId,
                                "Chunks devem ter novos IDs após reprocessamento");

                // Verificar que chunkIndex começa em 0
                assertEquals(0, newChunks.get(0).getChunkIndex(),
                                "Novo chunkIndex deve começar em 0");

                System.out.println("Primeiro chunk ID novo: " + firstNewChunkId);
                System.out.println("Reprocessamento concluído com " + newChunkCount + " chunks");
        }

        @Test
        @DisplayName("Deve remover documento e chunks ao deletar")
        void testDeleteDocumentRemovesChunksAlso() throws IOException {
                // Arrange - criar documento com chunks
                UUID userId = UUID.randomUUID();
                byte[] bytes = loadResource("sample.txt");
                UUID documentId = documentIngestionService.ingestDocument(userId, "para_deletar.txt", "text/plain",
                                bytes);

                // Verificar que documento e chunks existem
                assertTrue(documentRepository.existsById(documentId),
                                "Documento deve existir antes da deleção");

                List<DocumentChunk> chunksAntes = documentChunkRepository
                                .findByDocumentIdOrderByChunkIndexAsc(documentId);
                assertFalse(chunksAntes.isEmpty(),
                                "Chunks devem existir antes da deleção");

                System.out.println("Antes da deleção: " + chunksAntes.size() + " chunks");

                // Act - deletar usando o service
                documentIngestionService.deleteDocument(documentId);

                // Assert - documento não existe mais
                assertFalse(documentRepository.existsById(documentId),
                                "Documento não deve existir após deleção");

                // Assert - chunks não existem mais
                List<DocumentChunk> chunksDepois = documentChunkRepository
                                .findByDocumentIdOrderByChunkIndexAsc(documentId);
                assertTrue(chunksDepois.isEmpty(),
                                "Chunks não devem existir após deleção do documento");

                System.out.println("Após deleção: documento e chunks removidos com sucesso");
        }

        @Test
        @DisplayName("Deve salvar tamanho correto do arquivo")
        void testIngestDocumentSavesCorrectFileSize() throws IOException {
                // Arrange
                UUID userId = UUID.randomUUID();
                byte[] bytes = loadResource("sample.txt");

                // Act
                UUID documentId = documentIngestionService.ingestDocument(userId, "teste.txt", "text/plain", bytes);

                // Assert
                Document document = documentRepository.findById(documentId).orElseThrow();
                assertEquals(bytes.length, document.getSizeBytes(),
                                "SizeBytes deve corresponder ao tamanho do arquivo");

                System.out.println("Tamanho salvo corretamente: " + document.getSizeBytes() + " bytes");
        }

        @Test
        @DisplayName("Deve lançar exceção ao reprocessar documento inexistente")
        void testReprocessNonExistentDocument() {
                // Arrange
                UUID fakeDocumentId = UUID.randomUUID();

                // Act & Assert
                assertThrows(RuntimeException.class, () -> {
                        documentIngestionService.reprocessDocument(fakeDocumentId);
                });

                System.out.println("Exceção lançada corretamente para documento inexistente");
        }

        /**
         * Carrega um arquivo de recursos de teste.
         */
        private byte[] loadResource(String filename) throws IOException {
                try (InputStream is = getClass().getClassLoader().getResourceAsStream(filename)) {
                        if (is == null) {
                                throw new IOException("Recurso não encontrado: " + filename);
                        }
                        return is.readAllBytes();
                }
        }
}
