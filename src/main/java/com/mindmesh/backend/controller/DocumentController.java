package com.mindmesh.backend.controller;

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
 * Fornece endpoints para upload, listagem, deleção e reprocessamento.
 */
@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // TODO: Configurar CORS adequadamente em produção
@Tag(name = "Documents", description = """
        Gerenciamento de documentos do MindMesh.

        Permite upload de arquivos (PDF, DOCX, TXT), processamento automático
        com extração de texto, chunking inteligente e geração de embeddings vetoriais.
        """)
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentIngestionService documentIngestionService;

    @Operation(summary = "Upload de documento", description = """
            Faz upload e processamento completo de um documento.

            O processo inclui:
            1. Extração de texto (Apache Tika)
            2. Divisão em chunks semânticos
            3. Geração de embeddings via OpenAI
            4. Persistência no banco com PGVector

            Formatos suportados: PDF, DOCX, DOC, TXT, MD, HTML
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Documento processado com sucesso", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                    {
                        "documentId": "550e8400-e29b-41d4-a716-446655440000",
                        "filename": "relatorio.pdf",
                        "size": 102400,
                        "message": "Documento processado com sucesso"
                    }
                    """))),
            @ApiResponse(responseCode = "400", description = "Arquivo inválido ou vazio"),
            @ApiResponse(responseCode = "500", description = "Erro interno ao processar documento")
    })
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @Parameter(description = "ID do usuário dono do documento (UUID)", required = true, example = "550e8400-e29b-41d4-a716-446655440000") @RequestParam("userId") UUID userId,

            @Parameter(description = "Arquivo a ser processado (PDF, DOCX, TXT, etc.)", required = true) @RequestParam("file") MultipartFile file)
            throws IOException {

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

    @Operation(summary = "Listar documentos", description = """
            Retorna todos os documentos de um usuário específico.
            Os documentos são ordenados por data de criação (mais recente primeiro).
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de documentos retornada com sucesso"),
            @ApiResponse(responseCode = "400", description = "userId inválido")
    })
    @GetMapping("/list")
    public ResponseEntity<List<Document>> listDocuments(
            @Parameter(description = "ID do usuário para filtrar documentos", required = true, example = "550e8400-e29b-41d4-a716-446655440000") @RequestParam("userId") UUID userId) {

        log.info("Listando documentos do usuário: {}", userId);

        List<Document> documents = documentRepository.findByUserIdOrderByCreatedAtDesc(userId);

        log.info("Encontrados {} documentos para o usuário {}", documents.size(), userId);

        return ResponseEntity.ok(documents);
    }

    @Operation(summary = "Obter documento por ID", description = "Retorna os detalhes completos de um documento específico pelo seu ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Documento encontrado"),
            @ApiResponse(responseCode = "400", description = "Documento não encontrado")
    })
    @GetMapping("/{documentId}")
    public ResponseEntity<Document> getDocument(
            @Parameter(description = "ID único do documento (UUID)", required = true, example = "550e8400-e29b-41d4-a716-446655440000") @PathVariable UUID documentId) {

        log.info("Buscando documento: {}", documentId);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Documento não encontrado: " + documentId));

        return ResponseEntity.ok(document);
    }

    @Operation(summary = "Remover documento", description = """
            Remove um documento e todos os seus chunks associados.
            Esta operação é irreversível e remove também os embeddings do banco vetorial.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Documento removido com sucesso"),
            @ApiResponse(responseCode = "400", description = "Documento não encontrado")
    })
    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(
            @Parameter(description = "ID do documento a ser removido", required = true, example = "550e8400-e29b-41d4-a716-446655440000") @PathVariable UUID documentId) {

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

    @Operation(summary = "Reprocessar documento", description = """
            Reprocessa um documento existente, gerando novos chunks e embeddings.

            Útil quando:
            - O modelo de embeddings foi atualizado
            - A estratégia de chunking foi alterada
            - Houve erro no processamento original

            Remove todos os chunks antigos antes de gerar os novos.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Documento reprocessado com sucesso", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                    {
                        "documentId": "550e8400-e29b-41d4-a716-446655440000",
                        "chunksGenerated": 15,
                        "filename": "relatorio.pdf",
                        "message": "Documento reprocessado com sucesso"
                    }
                    """))),
            @ApiResponse(responseCode = "400", description = "Documento não encontrado"),
            @ApiResponse(responseCode = "500", description = "Erro ao reprocessar documento")
    })
    @PostMapping("/{documentId}/reprocess")
    public ResponseEntity<Map<String, Object>> reprocessDocument(
            @Parameter(description = "ID do documento a ser reprocessado", required = true, example = "550e8400-e29b-41d4-a716-446655440000") @PathVariable UUID documentId) {

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
