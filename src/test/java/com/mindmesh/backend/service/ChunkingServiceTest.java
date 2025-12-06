package com.mindmesh.backend.service;

import com.mindmesh.backend.model.DocumentChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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

        @BeforeEach
        void setUp() {
                // Configurar mock para retornar embedding fixo
                when(embeddingService.embed(anyString())).thenReturn(MOCK_EMBEDDING);
        }

        @Test
        @DisplayName("Deve dividir texto simples em chunks corretamente")
        void testChunkingSimpleText() {
                // Arrange
                String text = """
                                Este é o primeiro parágrafo do documento. Ele contém informações iniciais
                                sobre o tema que será abordado ao longo do texto.

                                Este é o segundo parágrafo. Aqui temos mais detalhes e explicações
                                sobre os conceitos introduzidos anteriormente.

                                O terceiro parágrafo traz conclusões parciais e prepara o leitor
                                para as próximas seções do documento.

                                Finalmente, o quarto parágrafo resume os pontos principais
                                e indica os próximos passos a serem seguidos.
                                """;

                // Act
                List<DocumentChunk> chunks = chunkingService.chunkAndEmbed(TEST_DOCUMENT_ID, text);

                // Assert
                assertNotNull(chunks, "Lista de chunks não deve ser nula");
                assertFalse(chunks.isEmpty(), "Lista de chunks não deve ser vazia");

                System.out.println("Chunks gerados: " + chunks.size());

                // Validar chunkIndex incremental
                for (int i = 0; i < chunks.size(); i++) {
                        DocumentChunk chunk = chunks.get(i);

                        assertEquals(i, chunk.getChunkIndex(),
                                        "ChunkIndex deve ser incremental começando em 0");

                        assertNotNull(chunk.getContent(),
                                        "Conteúdo do chunk não deve ser nulo");

                        assertTrue(chunk.getContent().length() <= 1000,
                                        "Chunk não deve ultrapassar MAX_CHUNK_SIZE (1000 chars)");

                        assertTrue(chunk.getTokenCount() > 0,
                                        "TokenCount deve ser maior que 0");

                        assertArrayEquals(MOCK_EMBEDDING, chunk.getEmbedding(),
                                        "Embedding deve ser igual ao mock");

                        assertEquals(TEST_DOCUMENT_ID, chunk.getDocumentId(),
                                        "DocumentId deve corresponder ao informado");

                        System.out.printf("Chunk %d: %d chars, %d tokens%n",
                                        i, chunk.getContent().length(), chunk.getTokenCount());
                }
        }

        @Test
        @DisplayName("Deve dividir parágrafo muito grande em múltiplos chunks")
        void testChunkingLongParagraphSplit() {
                // Arrange - criar texto de ~3000 caracteres sem quebras de parágrafo
                StringBuilder longText = new StringBuilder();
                for (int i = 0; i < 100; i++) {
                        longText.append("Esta é uma frase longa que será repetida várias vezes para criar um parágrafo extenso. ");
                }
                String text = longText.toString();

                System.out.println("Tamanho do texto original: " + text.length() + " chars");

                // Act
                List<DocumentChunk> chunks = chunkingService.chunkAndEmbed(TEST_DOCUMENT_ID, text);

                // Assert
                assertNotNull(chunks);
                assertTrue(chunks.size() > 1,
                                "Texto grande deve ser dividido em múltiplos chunks");

                System.out.println("Chunks gerados: " + chunks.size());

                // Validar limites de tamanho e índices incrementais
                for (int i = 0; i < chunks.size(); i++) {
                        DocumentChunk chunk = chunks.get(i);

                        assertEquals(i, chunk.getChunkIndex(),
                                        "ChunkIndex deve ser incremental");

                        assertTrue(chunk.getContent().length() <= 1000,
                                        "Nenhum chunk deve ultrapassar 1000 caracteres");

                        System.out.printf("Chunk %d: %d chars%n", i, chunk.getContent().length());
                }
        }

        @Test
        @DisplayName("Deve juntar parágrafos pequenos em um único chunk")
        void testChunkingMergesSmallParagraphs() {
                // Arrange - vários parágrafos pequenos (< 200 chars cada)
                String text = """
                                Parágrafo um.

                                Parágrafo dois.

                                Parágrafo três.

                                Parágrafo quatro.

                                Parágrafo cinco.
                                """;

                System.out.println("Texto com parágrafos pequenos: " + text.length() + " chars");

                // Act
                List<DocumentChunk> chunks = chunkingService.chunkAndEmbed(TEST_DOCUMENT_ID, text);

                // Assert
                assertNotNull(chunks);
                assertFalse(chunks.isEmpty());

                // Como são todos pequenos, devem ser juntados
                System.out.println("Chunks gerados: " + chunks.size());

                // Todos os parágrafos pequenos devem caber em poucos chunks
                assertTrue(chunks.size() <= 2,
                                "Parágrafos pequenos devem ser mesclados em poucos chunks");

                for (DocumentChunk chunk : chunks) {
                        assertTrue(chunk.getContent().length() >= 50,
                                        "Chunks mesclados devem ter tamanho razoável");

                        System.out.printf("Chunk mesclado: %d chars%n", chunk.getContent().length());
                }
        }

        @Test
        @DisplayName("Deve retornar lista vazia para texto nulo ou vazio")
        void testChunkingNullOrBlankText() {
                // Arrange & Act & Assert

                // Texto nulo
                List<DocumentChunk> chunksNull = chunkingService.chunkAndEmbed(TEST_DOCUMENT_ID, null);
                assertNotNull(chunksNull);
                assertTrue(chunksNull.isEmpty(), "Texto nulo deve retornar lista vazia");

                // Texto vazio
                List<DocumentChunk> chunksEmpty = chunkingService.chunkAndEmbed(TEST_DOCUMENT_ID, "");
                assertNotNull(chunksEmpty);
                assertTrue(chunksEmpty.isEmpty(), "Texto vazio deve retornar lista vazia");

                // Texto só com espaços
                List<DocumentChunk> chunksBlank = chunkingService.chunkAndEmbed(TEST_DOCUMENT_ID, "   \n\n   ");
                assertNotNull(chunksBlank);
                assertTrue(chunksBlank.isEmpty(), "Texto em branco deve retornar lista vazia");

                System.out.println("Textos nulos/vazios retornaram lista vazia corretamente");
        }

        @Test
        @DisplayName("Deve gerar metadata com chunk_index")
        void testChunkingGeneratesMetadata() {
                // Arrange
                String text = """
                                Primeiro parágrafo com conteúdo suficiente para formar um chunk.
                                Este texto deve ter pelo menos algumas dezenas de caracteres.

                                Segundo parágrafo também com conteúdo adequado para teste.
                                Precisamos garantir que temos múltiplos chunks.
                                """;

                // Act
                List<DocumentChunk> chunks = chunkingService.chunkAndEmbed(TEST_DOCUMENT_ID, text);

                // Assert
                assertNotNull(chunks);
                assertFalse(chunks.isEmpty());

                for (int i = 0; i < chunks.size(); i++) {
                        DocumentChunk chunk = chunks.get(i);

                        assertNotNull(chunk.getMetadata(),
                                        "Metadata não deve ser nula");

                        assertTrue(chunk.getMetadata().has("chunk_index"),
                                        "Metadata deve conter 'chunk_index'");

                        assertEquals(i, chunk.getMetadata().get("chunk_index").asInt(),
                                        "chunk_index na metadata deve corresponder ao índice");

                        System.out.printf("Chunk %d metadata: %s%n", i, chunk.getMetadata().toString());
                }
        }

        @Test
        @DisplayName("Deve gerar IDs únicos para cada chunk")
        void testChunkingGeneratesUniqueIds() {
                // Arrange
                String text = """
                                Parágrafo um com conteúdo.

                                Parágrafo dois com mais conteúdo.

                                Parágrafo três finaliza.
                                """;

                // Act
                List<DocumentChunk> chunks = chunkingService.chunkAndEmbed(TEST_DOCUMENT_ID, text);

                // Assert
                assertNotNull(chunks);

                // Verificar que todos os IDs são únicos
                long uniqueIds = chunks.stream()
                                .map(DocumentChunk::getId)
                                .distinct()
                                .count();

                assertEquals(chunks.size(), uniqueIds,
                                "Todos os chunks devem ter IDs únicos");

                // Verificar que nenhum ID é nulo
                for (DocumentChunk chunk : chunks) {
                        assertNotNull(chunk.getId(), "ID do chunk não deve ser nulo");
                }

                System.out.println("Todos os " + chunks.size() + " chunks têm IDs únicos");
        }
}
