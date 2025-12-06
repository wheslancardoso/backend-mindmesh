package com.mindmesh.backend.service;

import com.mindmesh.backend.model.Document;
import com.mindmesh.backend.model.DocumentChunk;
import com.mindmesh.backend.repository.DocumentChunkRepository;
import com.mindmesh.backend.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Serviço responsável pela ingestão completa de documentos.
 * Orquestra: extração de texto → chunking → embedding → persistência.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final TextExtractorService textExtractorService;
    private final ChunkingService chunkingService;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;

    /**
     * Ingere um documento: extrai texto, gera chunks com embeddings e persiste.
     *
     * @param userId      ID do usuário dono do documento
     * @param filename    Nome do arquivo original
     * @param contentType MIME type do arquivo
     * @param fileBytes   Conteúdo binário do arquivo
     * @return UUID do documento criado
     */
    @Transactional
    public UUID ingestDocument(UUID userId, String filename, String contentType, byte[] fileBytes) {
        long startTime = System.currentTimeMillis();

        log.info("Iniciando ingestão do documento: {} ({} bytes, tipo: {})",
                filename, fileBytes.length, contentType);

        try {
            // 1. Extrair texto do arquivo
            log.info("Extraindo texto do arquivo...");
            String text = textExtractorService.extractText(fileBytes, contentType);

            if (text == null || text.isBlank()) {
                throw new RuntimeException("Não foi possível extrair texto do arquivo: " + filename);
            }

            log.info("Texto extraído: {} caracteres", text.length());

            // 2. Criar entidade Document (sem texto ainda, para obter o ID)
            Document document = Document.builder()
                    .userId(userId)
                    .filename(filename)
                    .contentType(contentType)
                    .sizeBytes(fileBytes.length)
                    .build();

            // 3. Salvar documento para gerar ID
            document = documentRepository.save(document);
            UUID documentId = document.getId();

            log.info("Documento criado com ID: {}", documentId);

            // 4. Gerar chunks com embeddings
            log.info("Iniciando chunking e geração de embeddings...");
            List<DocumentChunk> chunks = chunkingService.chunkAndEmbed(documentId, text);

            log.info("Gerados {} chunks para o documento", chunks.size());

            // 5. Salvar todos os chunks
            documentChunkRepository.saveAll(chunks);

            log.info("Chunks salvos no banco de dados");

            // 6. Atualizar documento com o texto extraído
            document.setText(text);
            documentRepository.save(document);

            // 7. Calcular tempo total
            long elapsed = System.currentTimeMillis() - startTime;

            log.info("Ingestão concluída com sucesso! Documento: {}, Chunks: {}, Tempo: {}ms",
                    documentId, chunks.size(), elapsed);

            return documentId;

        } catch (Exception e) {
            log.error("Erro durante ingestão do documento {}: {}", filename, e.getMessage(), e);
            throw new RuntimeException("Falha na ingestão do documento '" + filename + "': " + e.getMessage(), e);
        }
    }

    /**
     * Reprocessa um documento existente (regenera chunks e embeddings).
     *
     * @param documentId ID do documento a reprocessar
     * @return Número de chunks gerados
     */
    @Transactional
    public int reprocessDocument(UUID documentId) {
        log.info("Reprocessando documento: {}", documentId);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Documento não encontrado: " + documentId));

        // Remover chunks antigos
        documentChunkRepository.deleteByDocumentId(documentId);

        // Regerar chunks
        List<DocumentChunk> chunks = chunkingService.chunkAndEmbed(documentId, document.getText());
        documentChunkRepository.saveAll(chunks);

        log.info("Documento {} reprocessado: {} novos chunks", documentId, chunks.size());

        return chunks.size();
    }

    /**
     * Remove um documento e todos os seus chunks.
     *
     * @param documentId ID do documento a remover
     */
    @Transactional
    public void deleteDocument(UUID documentId) {
        log.info("Removendo documento: {}", documentId);

        // Remover chunks primeiro (integridade referencial)
        documentChunkRepository.deleteByDocumentId(documentId);

        // Remover documento
        documentRepository.deleteById(documentId);

        log.info("Documento {} removido com sucesso", documentId);
    }
}
