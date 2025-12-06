package com.mindmesh.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.regex.Pattern;

/**
 * Implementação do TextExtractorService usando Apache Tika.
 * Suporta extração de texto de múltiplos formatos: PDF, DOCX, TXT, HTML, etc.
 */
@Slf4j
@Service
public class TextExtractorServiceImpl implements TextExtractorService {

    private static final Pattern MULTIPLE_SPACES = Pattern.compile("[ \\t]+");
    private static final Pattern MULTIPLE_NEWLINES = Pattern.compile("\\n{3,}");

    private final AutoDetectParser parser;

    public TextExtractorServiceImpl() {
        this.parser = new AutoDetectParser();
        log.info("TextExtractorService inicializado com Apache Tika");
    }

    /**
     * Extrai texto de um arquivo binário usando Apache Tika.
     *
     * @param bytes       Conteúdo binário do arquivo
     * @param contentType MIME type do arquivo (ex: "application/pdf")
     * @return Texto extraído e normalizado, ou null se vazio
     */
    @Override
    public String extractText(byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) {
            log.warn("Arquivo vazio recebido para extração");
            return null;
        }

        log.info("Extraindo texto de arquivo: {} bytes, tipo: {}", bytes.length, contentType);

        try {
            // Configurar metadata com o content type
            Metadata metadata = new Metadata();
            if (contentType != null && !contentType.isBlank()) {
                metadata.set(TikaCoreProperties.CONTENT_TYPE_PARSER_OVERRIDE, contentType);
            }

            // Handler com limite infinito (-1)
            BodyContentHandler handler = new BodyContentHandler(-1);

            // Parse do conteúdo
            try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
                parser.parse(inputStream, handler, metadata, new ParseContext());
            }

            // Obter texto extraído
            String extractedText = handler.toString();

            // Verificar se há conteúdo
            if (extractedText == null || extractedText.isBlank()) {
                log.warn("Nenhum texto extraído do arquivo");
                return null;
            }

            // Limpar e normalizar o texto
            String cleanedText = cleanText(extractedText);

            // Verificar novamente após limpeza
            if (cleanedText.isBlank()) {
                log.warn("Texto vazio após limpeza");
                return null;
            }

            log.info("Texto extraído com sucesso: {} caracteres", cleanedText.length());
            return cleanedText;

        } catch (Exception e) {
            log.error("Erro ao extrair texto: {}", e.getMessage(), e);
            throw new RuntimeException("Falha ao extrair texto do arquivo: " + e.getMessage(), e);
        }
    }

    /**
     * Limpa e normaliza o texto extraído.
     * - Remove espaços extras
     * - Normaliza quebras de linha
     * - Remove espaços no início/fim
     */
    private String cleanText(String text) {
        String cleaned = text.trim();

        // Substituir múltiplos espaços/tabs por um só espaço
        cleaned = MULTIPLE_SPACES.matcher(cleaned).replaceAll(" ");

        // Substituir 3+ quebras de linha por duas (parágrafos)
        cleaned = MULTIPLE_NEWLINES.matcher(cleaned).replaceAll("\n\n");

        return cleaned;
    }
}
