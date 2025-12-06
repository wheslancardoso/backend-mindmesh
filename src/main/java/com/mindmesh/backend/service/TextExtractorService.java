package com.mindmesh.backend.service;

/**
 * Interface para extração de texto de arquivos.
 * Implementações futuras: PDF, DOCX, TXT, etc.
 */
public interface TextExtractorService {

    /**
     * Extrai texto de um arquivo binário.
     *
     * @param bytes       Conteúdo binário do arquivo
     * @param contentType MIME type do arquivo (ex: "application/pdf")
     * @return Texto extraído do arquivo
     */
    String extractText(byte[] bytes, String contentType);
}
