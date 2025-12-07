package com.mindmesh.backend.controller;

import com.mindmesh.backend.dto.DocumentUploadResponseDto;
import com.mindmesh.backend.model.Document;
import com.mindmesh.backend.repository.DocumentChunkRepository;
import com.mindmesh.backend.repository.DocumentRepository;
import com.mindmesh.backend.service.DocumentIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller REST para gerenciamento de documentos.
 */
@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "Documents", description = "Upload, listagem e gerenciamento de documentos")
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentIngestionService documentIngestionService;

    @Operation(summary = "Upload de documento", description = """
            Faz upload e processamento completo de um documento.
            Retorna documentId, filename, fileHash, status e size.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Documento processado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Arquivo inválido ou vazio"),
            @ApiResponse(responseCode = "500", description = "Erro interno")
    })
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentUploadResponseDto> uploadDocument(
            @Parameter(description = "Arquivo a ser processado", required = true) @RequestParam("file") MultipartFile file,

            @Parameter(description = "ID do usuário", required = true) @RequestParam("userId") UUID userId)
            throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo não pode estar vazio");
        }

        String filename = file.getOriginalFilename();
        String contentType = file.getContentType();
        long size = file.getSize();

        log.info("Upload recebido - userId: {}, arquivo: '{}', tamanho: {} bytes",
                userId, filename, size);

        byte[] bytes = file.getBytes();
        Document document = documentIngestionService.ingestDocument(userId, filename, bytes, contentType);

        int chunksGenerated = documentIngestionService.getChunkCount(document.getId());

        log.info("Documento {} criado com {} chunks", document.getId(), chunksGenerated);

        return ResponseEntity.ok(DocumentUploadResponseDto.builder()
                .documentId(document.getId())
                .filename(document.getFilename())
                .fileHash(document.getFileHash())
                .status(document.getStatus())
                .size(size)
                .chunksGenerated(chunksGenerated)
                .message("Documento processado com sucesso")
                .createdAt(document.getCreatedAt())
                .build());
    }

    @Operation(summary = "Listar documentos do usuário")
    @GetMapping("/list")
    public ResponseEntity<List<Document>> listDocuments(
            @Parameter(description = "ID do usuário", required = true) @RequestParam("userId") UUID userId) {

        log.info("Listando documentos do usuário: {}", userId);
        List<Document> documents = documentRepository.findByUserIdOrderByCreatedAtDesc(userId);
        log.info("Encontrados {} documentos", documents.size());
        return ResponseEntity.ok(documents);
    }

    @Operation(summary = "Obter documento por ID")
    @GetMapping("/{documentId}")
    public ResponseEntity<Document> getDocument(
            @Parameter(description = "ID do documento", required = true) @PathVariable UUID documentId) {

        log.info("Buscando documento: {}", documentId);
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Documento não encontrado: " + documentId));
        return ResponseEntity.ok(document);
    }

    @Operation(summary = "Remover documento")
    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(
            @Parameter(description = "ID do documento", required = true) @PathVariable UUID documentId) {

        log.info("Removendo documento: {}", documentId);

        if (!documentRepository.existsById(documentId)) {
            throw new IllegalArgumentException("Documento não encontrado: " + documentId);
        }

        documentIngestionService.deleteDocument(documentId);
        log.info("Documento {} removido", documentId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleValidationError(IllegalArgumentException e) {
        log.warn("Erro de validação: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Requisição inválida",
                "message", e.getMessage()));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, String>> handleIOError(IOException e) {
        log.error("Erro de I/O: {}", e.getMessage(), e);
        return ResponseEntity.internalServerError().body(Map.of(
                "error", "Erro ao ler arquivo",
                "message", "Falha ao processar o arquivo enviado"));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeError(RuntimeException e) {
        log.error("Erro: {}", e.getMessage(), e);
        return ResponseEntity.internalServerError().body(Map.of(
                "error", "Erro interno",
                "message", "Falha ao processar a requisição"));
    }
}
