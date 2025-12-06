package com.mindmesh.backend.controller;

import com.mindmesh.backend.model.Document;
import com.mindmesh.backend.repository.DocumentChunkRepository;
import com.mindmesh.backend.repository.DocumentRepository;
import com.mindmesh.backend.service.DocumentIngestionService;
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
 * Fornece endpoints para upload, listagem, deleção e reprocessamento.
 */
@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // TODO: Configurar CORS adequadamente em produção
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentIngestionService documentIngestionService;

    /**
     * Upload e ingestão de documento.
     * Extrai texto, gera chunks com embeddings e persiste.
     *
     * @param userId ID do usuário dono do documento
     * @param file   Arquivo a ser processado (PDF, DOCX, TXT, etc.)
     * @return ID do documento criado
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("userId") UUID userId,
            @RequestParam("file") MultipartFile file) throws IOException {

        // Validar arquivo
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo não pode estar vazio");
        }

        String filename = file.getOriginalFilename();
        String contentType = file.getContentType();
        long size = file.getSize();

        log.info("Recebido upload - userId: {}, arquivo: '{}', tipo: {}, tamanho: {} bytes",
                userId, filename, contentType, size);

        // Extrair bytes e processar
        byte[] bytes = file.getBytes();
        UUID documentId = documentIngestionService.ingestDocument(userId, filename, contentType, bytes);

        log.info("Documento {} criado com sucesso para usuário {}", documentId, userId);

        return ResponseEntity.ok(Map.of(
                "documentId", documentId,
                "filename", filename != null ? filename : "",
                "size", size,
                "message", "Documento processado com sucesso"));
    }

    /**
     * Lista documentos de um usuário.
     *
     * @param userId ID do usuário
     * @return Lista de documentos ordenados por data de criação (mais recente
     *         primeiro)
     */
    @GetMapping("/list")
    public ResponseEntity<List<Document>> listDocuments(@RequestParam("userId") UUID userId) {
        log.info("Listando documentos do usuário: {}", userId);

        List<Document> documents = documentRepository.findByUserIdOrderByCreatedAtDesc(userId);

        log.info("Encontrados {} documentos para o usuário {}", documents.size(), userId);

        return ResponseEntity.ok(documents);
    }

    /**
     * Obtém detalhes de um documento específico.
     *
     * @param documentId ID do documento
     * @return Documento encontrado
     */
    @GetMapping("/{documentId}")
    public ResponseEntity<Document> getDocument(@PathVariable UUID documentId) {
        log.info("Buscando documento: {}", documentId);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Documento não encontrado: " + documentId));

        return ResponseEntity.ok(document);
    }

    /**
     * Remove um documento e seus chunks.
     *
     * @param documentId ID do documento a remover
     * @return 204 No Content
     */
    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID documentId) {
        log.info("Removendo documento: {}", documentId);

        // Verificar se documento existe
        if (!documentRepository.existsById(documentId)) {
            throw new IllegalArgumentException("Documento não encontrado: " + documentId);
        }

        // Remover chunks primeiro (integridade referencial)
        documentChunkRepository.deleteByDocumentId(documentId);

        // Remover documento
        documentRepository.deleteById(documentId);

        log.info("Documento {} removido com sucesso", documentId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Reprocessa um documento existente.
     * Remove chunks antigos e gera novos com embeddings atualizados.
     *
     * @param documentId ID do documento a reprocessar
     * @return Número de chunks gerados
     */
    @PostMapping("/{documentId}/reprocess")
    public ResponseEntity<Map<String, Object>> reprocessDocument(@PathVariable UUID documentId) {
        log.info("Reprocessando documento: {}", documentId);

        int chunksGenerated = documentIngestionService.reprocessDocument(documentId);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Documento não encontrado: " + documentId));

        log.info("Documento {} reprocessado com {} chunks", documentId, chunksGenerated);

        return ResponseEntity.ok(Map.of(
                "documentId", documentId,
                "chunksGenerated", chunksGenerated,
                "filename", document.getFilename(),
                "message", "Documento reprocessado com sucesso"));
    }

    /**
     * Handler de exceções para IllegalArgumentException (validação).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleValidationError(IllegalArgumentException e) {
        log.warn("Erro de validação: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Requisição inválida",
                "message", e.getMessage()));
    }

    /**
     * Handler de exceções para IOException (erro de upload).
     */
    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, String>> handleIOError(IOException e) {
        log.error("Erro de I/O: {}", e.getMessage(), e);
        return ResponseEntity.internalServerError().body(Map.of(
                "error", "Erro ao ler arquivo",
                "message", "Falha ao processar o arquivo enviado"));
    }

    /**
     * Handler de exceções genéricas.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeError(RuntimeException e) {
        log.error("Erro ao processar requisição: {}", e.getMessage(), e);
        return ResponseEntity.internalServerError().body(Map.of(
                "error", "Erro interno",
                "message", "Falha ao processar a requisição. Tente novamente."));
    }
}
