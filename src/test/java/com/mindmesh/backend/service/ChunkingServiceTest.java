package com.mindmesh.backend.service;

import com.mindmesh.backend.model.Document;
import com.mindmesh.backend.model.DocumentChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Testes unitários para ChunkingService.
 * Usa mock do EmbeddingService para testar isoladamente.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChunkingServiceTest {

        private static final float[] MOCK_EMBEDDING = new float[] { 0.1f, 0.2f, 0.3f };
        private static final UUID TEST_DOCUMENT_ID = UUID.randomUUID();

        @Mock
        private EmbeddingService embeddingService;

        @InjectMocks
        private ChunkingService chunkingService;

        private Document testDocument;

        @BeforeEach
        void setUp() {
                when(embeddingService.embed(anyString())).thenReturn(MOCK_EMBEDDING);

                testDocument = Document.builder()
                                .id(TEST_DOCUMENT_ID)
                                .userId(UUID.randomUUID())
                                .filename("test.txt")
                                .fileHash("abc123")
                                .status("processing")
                                .build();
        }

        @Test
        @DisplayName("Deve dividir texto simples em chunks corretamente")
        void testChunkingSimpleText() {
                String text = """
                                Este é o primeiro parágrafo do documento. Ele contém informações iniciais
                                sobre o tema que será abordado ao longo do texto.

                                Este é o segundo parágrafo. Aqui temos mais detalhes e explicações
                                sobre os conceitos introduzidos anteriormente.

                                O terceiro parágrafo traz conclusões parciais e prepara o leitor
                                para as próximas seções do documento.
                                """;

                List<DocumentChunk> chunks = chunkingService.chunkAndEmbed(testDocument, text);

                assertNotNull(chunks);
                assertFalse(chunks.isEmpty());

                for (int i = 0; i < chunks.size(); i++) {
                        DocumentChunk chunk = chunks.get(i);
                        assertEquals(i, chunk.getChunkIndex());
                        assertNotNull(chunk.getContent());
                        assertTrue(chunk.getContent().length() <= 1000);
                        assertTrue(chunk.getTokenCount() > 0);
                        assertArrayEquals(MOCK_EMBEDDING, chunk.getEmbedding());
                        assertEquals(testDocument, chunk.getDocument());
                }
        }

        @Test
        @DisplayName("Deve dividir parágrafo muito grande em múltiplos chunks")
        void testChunkingLongParagraphSplit() {
                StringBuilder longText = new StringBuilder();
                for (int i = 0; i < 100; i++) {
                        longText.append("Esta é uma frase longa repetida várias vezes para criar parágrafo extenso. ");
                }

                List<DocumentChunk> chunks = chunkingService.chunkAndEmbed(testDocument, longText.toString());

                assertNotNull(chunks);
                assertTrue(chunks.size() > 1);

                for (int i = 0; i < chunks.size(); i++) {
                        assertEquals(i, chunks.get(i).getChunkIndex());
                        assertTrue(chunks.get(i).getContent().length() <= 1000);
                }
        }

        @Test
        @DisplayName("Deve juntar parágrafos pequenos em um único chunk")
        void testChunkingMergesSmallParagraphs() {
                String text = """
                                Parágrafo um.

                                Parágrafo dois.

                                Parágrafo três.
                                """;

                List<DocumentChunk> chunks = chunkingService.chunkAndEmbed(testDocument, text);

                assertNotNull(chunks);
                assertFalse(chunks.isEmpty());
                assertTrue(chunks.size() <= 2);
        }

        @Test
        @DisplayName("Deve retornar lista vazia para texto nulo ou vazio")
        void testChunkingNullOrBlankText() {
                List<DocumentChunk> chunksNull = chunkingService.chunkAndEmbed(testDocument, null);
                assertTrue(chunksNull.isEmpty());

                List<DocumentChunk> chunksEmpty = chunkingService.chunkAndEmbed(testDocument, "");
                assertTrue(chunksEmpty.isEmpty());

                List<DocumentChunk> chunksBlank = chunkingService.chunkAndEmbed(testDocument, "   \n\n   ");
                assertTrue(chunksBlank.isEmpty());
        }

        @Test
        @DisplayName("Deve gerar metadata com chunk_index")
        void testChunkingGeneratesMetadata() {
                String text = """
                                Primeiro parágrafo com conteúdo suficiente para formar um chunk.
                                Este texto deve ter algumas dezenas de caracteres.

                                Segundo parágrafo também com conteúdo adequado para teste.
                                """;

                List<DocumentChunk> chunks = chunkingService.chunkAndEmbed(testDocument, text);

                assertNotNull(chunks);
                assertFalse(chunks.isEmpty());

                for (int i = 0; i < chunks.size(); i++) {
                        DocumentChunk chunk = chunks.get(i);
                        assertNotNull(chunk.getMetadata());
                        assertTrue(chunk.getMetadata().has("chunk_index"));
                        assertEquals(i, chunk.getMetadata().get("chunk_index").asInt());
                }
        }

        @Test
        @DisplayName("Deve gerar IDs únicos para cada chunk")
        void testChunkingGeneratesUniqueIds() {
                String text = """
                                Parágrafo um com conteúdo.

                                Parágrafo dois com mais conteúdo.

                                Parágrafo três finaliza.
                                """;

                List<DocumentChunk> chunks = chunkingService.chunkAndEmbed(testDocument, text);

                assertNotNull(chunks);

                long uniqueIds = chunks.stream()
                                .map(DocumentChunk::getId)
                                .distinct()
                                .count();

                assertEquals(chunks.size(), uniqueIds);

                for (DocumentChunk chunk : chunks) {
                        assertNotNull(chunk.getId());
                }
        }
}
