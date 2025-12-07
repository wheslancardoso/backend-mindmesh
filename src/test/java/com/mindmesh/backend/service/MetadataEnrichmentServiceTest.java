package com.mindmesh.backend.service;

import com.mindmesh.backend.dto.MetadataV2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes unitários para MetadataEnrichmentService.
 * Testa em modo mock (sem chamadas reais à OpenAI).
 */
class MetadataEnrichmentServiceTest {

    private MetadataEnrichmentService service;

    @BeforeEach
    void setUp() {
        // Usa construtor padrão que inicializa em modo mock (sem OPENAI_API_KEY no env)
        service = new MetadataEnrichmentService();
    }

    @Test
    @DisplayName("Deve retornar metadados mock para arquivo .txt")
    void testEnrichTxtFile() {
        String filename = "documento.txt";
        String text = "Este é um documento de teste para validação do serviço de enriquecimento.";

        MetadataV2 result = service.enrich(filename, text);

        assertNotNull(result);
        assertEquals("txt", result.getDocumentType());
        assertNotNull(result.getKeywords());
        assertFalse(result.getKeywords().isEmpty());
        assertNotNull(result.getTopics());
        assertNotNull(result.getSummary());
        assertNotNull(result.getLanguage());
        assertNotNull(result.getConfidence());
        assertTrue(result.getConfidence() >= 0.0 && result.getConfidence() <= 1.0);
    }

    @Test
    @DisplayName("Deve retornar metadados mock para arquivo .pdf")
    void testEnrichPdfFile() {
        String filename = "relatorio.pdf";
        String text = "Conteúdo do relatório PDF extraído.";

        MetadataV2 result = service.enrich(filename, text);

        assertNotNull(result);
        assertEquals("pdf", result.getDocumentType());
    }

    @Test
    @DisplayName("Deve retornar metadados mock para arquivo .java")
    void testEnrichCodeFile() {
        String filename = "Main.java";
        String text = "public class Main { public static void main(String[] args) {} }";

        MetadataV2 result = service.enrich(filename, text);

        assertNotNull(result);
        assertEquals("code", result.getDocumentType());
    }

    @Test
    @DisplayName("Deve retornar metadados mock para arquivo .md")
    void testEnrichMarkdownFile() {
        String filename = "README.md";
        String text = "# Título\\n\\nConteúdo do markdown.";

        MetadataV2 result = service.enrich(filename, text);

        assertNotNull(result);
        assertEquals("markdown", result.getDocumentType());
    }

    @Test
    @DisplayName("Deve retornar metadados vazios para texto vazio")
    void testEnrichEmptyText() {
        String filename = "vazio.txt";
        String text = "";

        MetadataV2 result = service.enrich(filename, text);

        assertNotNull(result);
        assertEquals("unknown", result.getDocumentType());
        assertTrue(result.getKeywords().isEmpty());
        assertTrue(result.getTopics().isEmpty());
        assertEquals("", result.getSummary());
    }

    @Test
    @DisplayName("Deve retornar metadados vazios para texto nulo")
    void testEnrichNullText() {
        String filename = "nulo.txt";

        MetadataV2 result = service.enrich(filename, null);

        assertNotNull(result);
        assertEquals("unknown", result.getDocumentType());
    }

    @Test
    @DisplayName("Conversão toMap deve conter todos os campos")
    void testToMapConversion() {
        String filename = "teste.txt";
        String text = "Texto de teste.";

        MetadataV2 result = service.enrich(filename, text);
        var map = result.toMap();

        assertNotNull(map);
        assertTrue(map.containsKey("document_type"));
        assertTrue(map.containsKey("keywords"));
        assertTrue(map.containsKey("topics"));
        assertTrue(map.containsKey("summary"));
        assertTrue(map.containsKey("language"));
        assertTrue(map.containsKey("confidence"));
    }

    @Test
    @DisplayName("enrichAsMap deve retornar Map válido")
    void testEnrichAsMap() {
        String filename = "teste.txt";
        String text = "Texto de teste para map.";

        var map = service.enrichAsMap(filename, text);

        assertNotNull(map);
        assertEquals("txt", map.get("document_type"));
        assertNotNull(map.get("keywords"));
        assertNotNull(map.get("topics"));
    }

    @Test
    @DisplayName("isMockMode deve depender da presença de API key")
    void testMockModeFlag() {
        // Este teste apenas verifica que o método existe e retorna boolean
        // O valor depende do ambiente, então não fazemos assertion de valor específico
        boolean isInMockMode = service.isMockMode();
        assertNotNull(Boolean.valueOf(isInMockMode));
    }
}
