package com.mindmesh.backend.dto;

import com.mindmesh.backend.model.Document;

/**
 * Resultado da ingestão de documento.
 * Indica se o documento é novo ou duplicado.
 *
 * @param document    Documento persistido (novo ou existente)
 * @param isDuplicate true se o documento já existia para o usuário
 */
public record IngestionResult(Document document, boolean isDuplicate) {

    /**
     * Factory para documento novo.
     */
    public static IngestionResult newDocument(Document document) {
        return new IngestionResult(document, false);
    }

    /**
     * Factory para documento duplicado.
     */
    public static IngestionResult duplicate(Document document) {
        return new IngestionResult(document, true);
    }
}
