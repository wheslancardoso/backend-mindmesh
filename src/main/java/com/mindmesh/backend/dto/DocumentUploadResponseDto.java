package com.mindmesh.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de resposta para upload de documento.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resposta do upload de documento")
public class DocumentUploadResponseDto {

    @Schema(description = "ID único do documento", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID documentId;

    @Schema(description = "Nome do arquivo", example = "relatorio.pdf")
    private String filename;

    @Schema(description = "Hash SHA-256 do arquivo", example = "a1b2c3d4e5f6...")
    private String fileHash;

    @Schema(description = "Status do documento", example = "processed")
    private String status;

    @Schema(description = "Tamanho do arquivo em bytes", example = "102400")
    private long size;

    @Schema(description = "Número de chunks gerados", example = "15")
    private int chunksGenerated;

    @Schema(description = "Mensagem informativa", example = "Documento processado com sucesso")
    private String message;

    @Schema(description = "Data de criação")
    private LocalDateTime createdAt;
}
