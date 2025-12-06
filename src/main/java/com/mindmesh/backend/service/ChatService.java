package com.mindmesh.backend.service;

import com.mindmesh.backend.dto.ChatRequestDto;
import com.mindmesh.backend.dto.ChatResponseDto;
import com.mindmesh.backend.dto.RetrievedChunkDto;
import com.mindmesh.backend.model.DocumentChunk;
import com.mindmesh.backend.repository.DocumentChunkRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Serviço de chat com RAG (Retrieval-Augmented Generation).
 * Implementa busca semântica + geração de resposta via LLM.
 */
@Slf4j
@Service
public class ChatService {

    private static final String SYSTEM_PROMPT = """
            Você é um assistente inteligente do MindMesh.
            Responda APENAS com base no contexto fornecido abaixo.
            Se não houver informação suficiente no contexto para responder, diga claramente:
            "Não encontrei informação suficiente nos documentos para responder essa pergunta."

            Seja preciso, objetivo e cite as fontes quando possível.
            """;

    private static final int MAX_SNIPPET_LENGTH = 200;

    private final EmbeddingService embeddingService;
    private final DocumentChunkRepository documentChunkRepository;
    private final ChatLanguageModel chatModel;

    public ChatService(
            EmbeddingService embeddingService,
            DocumentChunkRepository documentChunkRepository,
            @Value("${openai.api.key}") String apiKey) {
        this.embeddingService = embeddingService;
        this.documentChunkRepository = documentChunkRepository;

        this.chatModel = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gpt-4o-mini")
                .temperature(0.3)
                .build();

        log.info("ChatService inicializado com modelo: gpt-4o-mini");
    }

    /**
     * Processa uma pergunta do usuário usando RAG.
     *
     * @param request Requisição contendo pergunta, userId e filtros
     * @return Resposta com answer e chunks usados
     */
    public ChatResponseDto chat(ChatRequestDto request) {
        long startTime = System.currentTimeMillis();

        // 1. Validar input
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new IllegalArgumentException("A pergunta não pode estar vazia");
        }

        if (request.getUserId() == null) {
            throw new IllegalArgumentException("userId é obrigatório");
        }

        log.info("Processando pergunta: '{}' para usuário: {}",
                truncate(request.getQuestion(), 100), request.getUserId());

        try {
            // 2. Gerar embedding da pergunta
            log.info("Gerando embedding da pergunta...");
            float[] queryVector = embeddingService.embed(request.getQuestion());

            // 3. Buscar chunks mais relevantes via busca vetorial
            log.info("Buscando chunks similares...");
            List<DocumentChunk> retrievedChunks = documentChunkRepository.findSimilar(
                    queryVector,
                    request.getUserId(),
                    request.getMetadataFilter(),
                    request.getEffectiveLimit());

            log.info("Recuperados {} chunks", retrievedChunks.size());

            if (retrievedChunks.isEmpty()) {
                return ChatResponseDto.builder()
                        .answer("Não encontrei documentos relevantes para responder sua pergunta. " +
                                "Por favor, verifique se você já fez upload de documentos relacionados ao tema.")
                        .sources(List.of())
                        .processingTimeMs(System.currentTimeMillis() - startTime)
                        .build();
            }

            // TODO: Implementar reranking ONNX aqui no futuro
            // Os chunks retornados pela busca vetorial podem ser reordenados
            // usando um modelo de reranking (cross-encoder) para melhorar a precisão.
            // Exemplo: retrievedChunks = rerankingService.rerank(request.getQuestion(),
            // retrievedChunks);

            // 4. Montar contexto com os chunks
            String context = buildContext(retrievedChunks);

            // 5. Montar prompt final
            String prompt = buildPrompt(context, request.getQuestion());

            // 6. Chamar o modelo de chat
            log.info("Chamando modelo de chat...");
            String answer = chatModel.generate(prompt);

            // 7. Montar resposta com sources
            List<RetrievedChunkDto> sources = retrievedChunks.stream()
                    .map(this::toRetrievedChunkDto)
                    .collect(Collectors.toList());

            long processingTime = System.currentTimeMillis() - startTime;

            log.info("Resposta gerada com sucesso. Chunks usados: {}, Tempo: {}ms",
                    sources.size(), processingTime);

            return ChatResponseDto.builder()
                    .answer(answer)
                    .sources(sources)
                    .processingTimeMs(processingTime)
                    .build();

        } catch (Exception e) {
            log.error("Erro ao processar chat: {}", e.getMessage(), e);
            throw new RuntimeException("Falha ao processar a pergunta: " + e.getMessage(), e);
        }
    }

    /**
     * Monta o contexto a partir dos chunks recuperados.
     */
    private String buildContext(List<DocumentChunk> chunks) {
        StringBuilder context = new StringBuilder();

        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            context.append("Fonte ").append(i + 1).append(":\n");
            context.append(chunk.getContent());
            context.append("\n\n");
        }

        return context.toString().trim();
    }

    /**
     * Monta o prompt completo para o modelo.
     */
    private String buildPrompt(String context, String question) {
        return String.format("""
                %s

                CONTEXTO:
                %s

                PERGUNTA DO USUÁRIO:
                %s

                RESPOSTA:
                """, SYSTEM_PROMPT, context, question);
    }

    /**
     * Converte DocumentChunk para DTO.
     */
    private RetrievedChunkDto toRetrievedChunkDto(DocumentChunk chunk) {
        return RetrievedChunkDto.builder()
                .id(chunk.getId())
                .documentId(chunk.getDocumentId())
                .snippet(truncate(chunk.getContent(), MAX_SNIPPET_LENGTH))
                .chunkIndex(chunk.getChunkIndex())
                .build();
    }

    /**
     * Trunca texto para o tamanho máximo especificado.
     */
    private String truncate(String text, int maxLength) {
        if (text == null)
            return "";
        if (text.length() <= maxLength)
            return text;
        return text.substring(0, maxLength - 3) + "...";
    }
}
