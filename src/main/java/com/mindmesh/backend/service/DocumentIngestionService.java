package com.mindmesh.backend.service;

import com.mindmesh.backend.dto.IngestionResult;
import com.mindmesh.backend.model.Document;
import com.mindmesh.backend.model.DocumentChunk;
import com.mindmesh.backend.repository.DocumentChunkRepository;
import com.mindmesh.backend.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Serviço de ingestão de documentos.
 * Status flow: pending → processing → completed (ou failed).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final TextExtractorService textExtractorService;
    private final ChunkingService chunkingService;
    private final MetadataEnrichmentService metadataEnrichmentService;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;

    /**
     * Ingere um documento: extrai texto, gera chunks com embeddings e persiste.
     * Retorna IngestionResult com flag isDuplicate para identificar duplicatas.
     * Status: pending → processing → completed
     */
    @Transactional
    public IngestionResult ingestDocument(UUID userId, String filename, byte[] fileBytes, String contentType) {
        long startTime = System.currentTimeMillis();

        log.info("[userId={}] Iniciando ingestão: {} ({} bytes)", userId, filename, fileBytes.length);

        // 1. Calcular hash SHA-256 para deduplicação
        String fileHash = calculateHash(fileBytes);

        // 2. Verificar duplicata - retorna IngestionResult com flag
        Optional<Document> existing = documentRepository.findByUserIdAndFileHash(userId, fileHash);
        if (existing.isPresent()) {
            log.warn("[userId={}] Documento duplicado: {} (hash: {})", userId, filename, fileHash);
            return IngestionResult.duplicate(existing.get());
        }

        // 3. Criar documento com status PENDING
        Document document = Document.builder()
                .userId(userId)
                .filename(filename)
                .fileHash(fileHash)
                .status("pending")
                .build();

        document = documentRepository.save(document);
        UUID documentId = document.getId();
        log.info("[userId={}, documentId={}] Documento criado", userId, documentId);

        try {
            // 4. Atualizar status para PROCESSING
            document.setStatus("processing");
            documentRepository.save(document);

            // 5. Extrair texto
            log.info("[documentId={}] Extraindo texto...", documentId);
            String text = textExtractorService.extractText(fileBytes, contentType);

            if (text == null || text.isBlank()) {
                document.setStatus("failed");
                documentRepository.save(document);
                throw new RuntimeException("Não foi possível extrair texto do arquivo: " + filename);
            }

            log.info("[documentId={}] Texto extraído: {} caracteres", documentId, text.length());

            // 6. Enriquecer documento com metadados AI (UMA chamada OpenAI)
            log.info("[documentId={}] Enriquecendo metadados V2...", documentId);
            Map<String, Object> documentMetadata = metadataEnrichmentService.enrichAsMap(filename, text);
            log.info("[documentId={}] Metadados V2 gerados: {}", documentId, documentMetadata.keySet());

            // 7. Gerar chunks com embeddings e metadados
            log.info("[documentId={}] Gerando chunks e embeddings...", documentId);
            List<DocumentChunk> chunks = chunkingService.chunkAndEmbed(document, text, documentMetadata);

            log.info("[documentId={}] Gerados {} chunks", documentId, chunks.size());

            // 7. Salvar chunks
            documentChunkRepository.saveAll(chunks);

            // 8. Atualizar status para COMPLETED
            document.setStatus("completed");
            documentRepository.save(document);

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[documentId={}] Ingestão concluída! Chunks: {}, Tempo: {}ms",
                    documentId, chunks.size(), elapsed);

            return IngestionResult.newDocument(document);

        } catch (Exception e) {
            log.error("[documentId={}] Erro na ingestão: {}", documentId, e.getMessage(), e);
            document.setStatus("failed");
            documentRepository.save(document);
            throw e;
        }
    }

    /**
     * Retorna o número de chunks gerados.
     */
    @Transactional(readOnly = true)
    public int getChunkCount(UUID documentId) {
        return (int) documentChunkRepository.countByDocumentId(documentId);
    }

    /**
     * Remove documento e todos os seus chunks.
     */
    @Transactional
    public void deleteDocument(UUID documentId) {
        log.info("[documentId={}] Removendo documento...", documentId);

        documentChunkRepository.deleteByDocumentId(documentId);
        documentRepository.deleteById(documentId);

        log.info("[documentId={}] Documento removido", documentId);
    }

    /**
     * Calcula hash SHA-256 do conteúdo (64 caracteres hex).
     */
    private String calculateHash(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 não suportado", e);
        }
    }
}
