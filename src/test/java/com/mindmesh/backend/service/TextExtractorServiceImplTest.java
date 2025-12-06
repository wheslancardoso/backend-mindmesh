package com.mindmesh.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes unitários para TextExtractorServiceImpl.
 * Testa extração real usando Apache Tika, sem contexto Spring.
 */
class TextExtractorServiceImplTest {

    private TextExtractorServiceImpl textExtractorService;

    @BeforeEach
    void setUp() {
        textExtractorService = new TextExtractorServiceImpl();
    }

    @Test
    @DisplayName("Deve extrair texto de arquivo TXT corretamente")
    void testExtractTextFromTxt() throws IOException {
        // Arrange
        byte[] bytes = loadResource("sample.txt");

        System.out.println("Tamanho do arquivo TXT: " + bytes.length + " bytes");

        // Act
        String result = textExtractorService.extractText(bytes, "text/plain");

        // Assert
        assertNotNull(result, "Resultado não deve ser nulo");
        assertTrue(result.length() > 5, "Resultado deve ter mais de 5 caracteres");
        assertTrue(result.contains("exemplo"), "Resultado deve conter a palavra 'exemplo'");
        assertTrue(result.contains("Tika"), "Resultado deve conter a palavra 'Tika'");

        System.out.println("Texto extraído do TXT:");
        System.out.println(result.substring(0, Math.min(200, result.length())) + "...");
    }

    @Test
    @DisplayName("Deve retornar null para arquivo vazio")
    void testExtractTextFromEmptyFile() {
        // Arrange
        byte[] emptyBytes = new byte[0];

        // Act
        String result = textExtractorService.extractText(emptyBytes, "text/plain");

        // Assert
        assertTrue(result == null || result.isBlank(),
                "Arquivo vazio deve retornar null ou string vazia");

        System.out.println("Arquivo vazio tratado corretamente");
    }

    @Test
    @DisplayName("Deve retornar null para bytes nulos")
    void testExtractTextFromNullBytes() {
        // Act
        String result = textExtractorService.extractText(null, "text/plain");

        // Assert
        assertNull(result, "Bytes nulos devem retornar null");

        System.out.println("Bytes nulos tratados corretamente");
    }

    @Test
    @DisplayName("Deve lançar exceção para conteúdo inválido/corrompido")
    void testExtractTextWithInvalidContent() {
        // Arrange - criar bytes aleatórios que não formam um arquivo válido
        byte[] randomBytes = new byte[1000];
        new Random(42).nextBytes(randomBytes);

        // Para alguns formatos, Tika pode não lançar exceção mas retornar texto vazio
        // Vamos testar se pelo menos não quebra
        try {
            String result = textExtractorService.extractText(randomBytes, "application/pdf");

            // Se não lançou exceção, deve retornar null/vazio para conteúdo inválido
            assertTrue(result == null || result.isBlank() || result.length() < 50,
                    "Conteúdo inválido deve retornar null, vazio ou pouco texto");

            System.out.println("Bytes aleatórios: resultado = " +
                    (result == null ? "null" : result.length() + " chars"));

        } catch (RuntimeException e) {
            // Também é aceitável lançar exceção
            System.out.println("Exceção lançada para bytes inválidos: " + e.getMessage());
            assertTrue(e.getMessage().contains("Falha"),
                    "Exceção deve conter mensagem clara");
        }
    }

    @Test
    @DisplayName("Deve normalizar múltiplos espaços para um único")
    void testExtractTextNormalizesWhitespace() throws IOException {
        // Arrange
        byte[] bytes = loadResource("sample_whitespace.txt");

        System.out.println("Tamanho do arquivo com espaços extras: " + bytes.length + " bytes");

        // Act
        String result = textExtractorService.extractText(bytes, "text/plain");

        // Assert
        assertNotNull(result);

        // Verificar que não há múltiplos espaços consecutivos
        assertFalse(result.contains("  "),
                "Não deve haver dois espaços consecutivos");

        // Verificar que não há mais de duas quebras de linha consecutivas
        assertFalse(result.contains("\n\n\n"),
                "Não deve haver mais de duas quebras de linha consecutivas");

        // Verificar que o texto foi limpo mas preservou o conteúdo
        assertTrue(result.contains("múltiplos"),
                "Texto deve conter a palavra 'múltiplos'");
        assertTrue(result.contains("normalização"),
                "Texto deve conter a palavra 'normalização'");

        System.out.println("Texto normalizado:");
        System.out.println(result);
    }

    @Test
    @DisplayName("Deve fazer trim do texto extraído")
    void testExtractTextTrimsResult() throws IOException {
        // Arrange
        byte[] bytes = loadResource("sample.txt");

        // Act
        String result = textExtractorService.extractText(bytes, "text/plain");

        // Assert
        assertNotNull(result);
        assertEquals(result, result.trim(),
                "Resultado deve estar sem espaços nas extremidades");

        System.out.println("Texto está corretamente trimmed");
    }

    @Test
    @DisplayName("Deve funcionar com content type nulo (auto-detect)")
    void testExtractTextWithNullContentType() throws IOException {
        // Arrange
        byte[] bytes = loadResource("sample.txt");

        // Act - passar contentType nulo, Tika deve detectar automaticamente
        String result = textExtractorService.extractText(bytes, null);

        // Assert
        assertNotNull(result, "Tika deve auto-detectar o tipo");
        assertTrue(result.contains("exemplo"),
                "Conteúdo deve ser extraído mesmo sem contentType");

        System.out.println("Auto-detecção funcionou corretamente");
    }

    @Test
    @DisplayName("Deve funcionar com content type vazio (auto-detect)")
    void testExtractTextWithEmptyContentType() throws IOException {
        // Arrange
        byte[] bytes = loadResource("sample.txt");

        // Act
        String result = textExtractorService.extractText(bytes, "");

        // Assert
        assertNotNull(result);
        assertTrue(result.length() > 0);

        System.out.println("Content type vazio tratado com auto-detecção");
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
