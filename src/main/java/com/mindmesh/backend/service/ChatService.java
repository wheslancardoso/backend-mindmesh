package com.mindmesh.backend.service;

import com.mindmesh.backend.dto.ChatRequestDto;
import com.mindmesh.backend.dto.ChatResponseDto;
import com.mindmesh.backend.dto.ChunkSearchResult;
import com.mindmesh.backend.dto.RetrievedChunkDto;
import com.mindmesh.backend.model.ChatMessage;
import com.mindmesh.backend.model.ChatSession;
import com.mindmesh.backend.repository.ChatMessageRepository;
import com.mindmesh.backend.repository.ChatSessionRepository;
import com.mindmesh.backend.repository.DocumentChunkRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Serviço de chat com RAG (Retrieval-Augmented Generation).
 * Implementa busca semântica + geração de resposta via LLM.
 * Registra usedChunkIds para rastreabilidade e feedback loop.
 */
@Slf4j
@Service
public class ChatService {

    private static final String SYSTEM_PROMPT = """
            Você é um assistente especializado em análise de documentos usando RAG (Retrieval-Augmented Generation).

            REGRAS IMPORTANTES:
            1. Use SEMPRE tanto os METADADOS quanto os CONTEÚDOS dos chunks para responder.
            2. Se a resposta estiver claramente presente nos metadados (document_type, language, keywords, topics, summary),
               utilize essas informações diretamente.
            3. Cite as fontes quando possível (ex: "De acordo com o documento...").
            4. Se perguntarem sobre o tipo, idioma, palavras-chave ou resumo do documento,
               responda usando os metadados disponíveis.
            5. Se realmente não houver informação suficiente nos metadados nem nos chunks, responda:
               "Não encontrei informação suficiente nos documentos para responder essa pergunta."

            Seja preciso, objetivo e útil.
            """;

    private static final int MAX_SNIPPET_LENGTH = 300;

    private final EmbeddingService embeddingService;
    private final DocumentChunkRepository documentChunkRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatLanguageModel chatModel;
    private final boolean mockMode;

    public ChatService(
            EmbeddingService embeddingService,
            DocumentChunkRepository documentChunkRepository,
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository) {

        this.embeddingService = embeddingService;
        this.documentChunkRepository = documentChunkRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;

        String apiKey = System.getenv("OPENAI_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("⚠️  OPENAI_API_KEY não definida - ChatService em MODO MOCK");
            this.mockMode = true;
            this.chatModel = null;
        } else {
            this.mockMode = false;
            this.chatModel = OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .modelName("gpt-4o-mini")
                    .temperature(0.3)
                    .build();
            log.info("ChatService inicializado: gpt-4o-mini");
        }
    }

    /**
     * Processa uma mensagem do usuário usando RAG.
     */
    @Transactional
    public ChatResponseDto chat(ChatRequestDto request) {
        long startTime = System.currentTimeMillis();

        // Validações
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            throw new IllegalArgumentException("A mensagem não pode estar vazia");
        }
        if (request.getUserId() == null) {
            throw new IllegalArgumentException("userId é obrigatório");
        }

        UUID userId = request.getUserId();
        log.info("[userId={}] Processando mensagem: '{}'", userId, truncate(request.getMessage(), 100));

        // 1. Obter ou criar sessão
        ChatSession session = getOrCreateSession(request.getSessionId(), userId);
        UUID sessionId = session.getId();
        log.info("[userId={}, sessionId={}] Sessão ativa", userId, sessionId);

        // 2. Salvar mensagem do usuário
        ChatMessage userMessage = ChatMessage.builder()
                .session(session)
                .role("user")
                .content(request.getMessage())
                .build();
        chatMessageRepository.save(userMessage);

        // 3. Gerar embedding da pergunta
        log.info("[sessionId={}] Gerando embedding...", sessionId);
        float[] queryVector = embeddingService.embed(request.getMessage());

        // 4. Buscar chunks similares
        log.info("[sessionId={}] Buscando chunks similares...", sessionId);
        List<ChunkSearchResult> retrievedChunks = documentChunkRepository.findSimilar(
                queryVector,
                userId,
                request.getMetadataFilters(),
                request.getEffectiveLimit());

        log.info("[sessionId={}] Recuperados {} chunks", sessionId, retrievedChunks.size());

        // 5. Gerar resposta
        String answer;
        UUID[] usedChunkIds;

        if (retrievedChunks.isEmpty()) {
            answer = "Não encontrei documentos relevantes para responder sua pergunta. " +
                    "Por favor, verifique se você já fez upload de documentos relacionados ao tema.";
            usedChunkIds = new UUID[0];
        } else {
            String context = buildContext(retrievedChunks);
            String prompt = buildPrompt(context, request.getMessage());

            // Extrair IDs dos chunks usados
            usedChunkIds = retrievedChunks.stream()
                    .map(ChunkSearchResult::getId)
                    .toArray(UUID[]::new);

            if (mockMode) {
                answer = "[MOCK] Resposta simulada para: " + request.getMessage();
            } else {
                log.info("[sessionId={}] Chamando modelo de chat...", sessionId);
                answer = chatModel.generate(prompt);
            }
        }

        // 6. Salvar resposta do assistente COM usedChunkIds
        ChatMessage assistantMessage = ChatMessage.builder()
                .session(session)
                .role("assistant")
                .content(answer)
                .usedChunkIds(usedChunkIds)
                .build();
        chatMessageRepository.save(assistantMessage);

        // 7. Atualizar título da sessão
        if (session.getTitle() == null || session.getTitle().equals("Nova conversa")) {
            session.setTitle(generateTitle(request.getMessage()));
            chatSessionRepository.save(session);
        }

        // 8. Montar resposta
        List<RetrievedChunkDto> chunks = retrievedChunks.stream()
                .map(this::toRetrievedChunkDto)
                .collect(Collectors.toList());

        long processingTime = System.currentTimeMillis() - startTime;
        log.info("[sessionId={}] Resposta gerada. Chunks: {}, Tempo: {}ms", sessionId, chunks.size(), processingTime);

        return ChatResponseDto.builder()
                .sessionId(sessionId)
                .answer(answer)
                .chunks(chunks)
                .messageId(assistantMessage.getId())
                .build();
    }

    private ChatSession getOrCreateSession(UUID sessionId, UUID userId) {
        if (sessionId != null) {
            return chatSessionRepository.findById(sessionId)
                    .orElseGet(() -> createNewSession(userId));
        }
        return createNewSession(userId);
    }

    private ChatSession createNewSession(UUID userId) {
        ChatSession session = ChatSession.builder()
                .userId(userId)
                .title("Nova conversa")
                .build();
        return chatSessionRepository.save(session);
    }

    private String generateTitle(String message) {
        if (message.length() <= 50) {
            return message;
        }
        return message.substring(0, 47) + "...";
    }

    private String buildContext(List<ChunkSearchResult> chunks) {
        StringBuilder context = new StringBuilder();

        for (int i = 0; i < chunks.size(); i++) {
            ChunkSearchResult chunk = chunks.get(i);
            context.append("=== Documento/Chunk ").append(i + 1).append(" ===\n");

            // Incluir metadados enriquecidos se disponíveis
            String metadata = chunk.getMetadata();
            if (metadata != null && !metadata.isBlank() && !metadata.equals("null")) {
                try {
                    com.fasterxml.jackson.databind.JsonNode metaNode = new com.fasterxml.jackson.databind.ObjectMapper()
                            .readTree(metadata);

                    context.append("METADADOS:\n");

                    if (metaNode.has("document_type") && !metaNode.get("document_type").asText().isBlank()) {
                        context.append("  Tipo: ").append(metaNode.get("document_type").asText()).append("\n");
                    }
                    if (metaNode.has("language") && !metaNode.get("language").asText().isBlank()) {
                        context.append("  Idioma: ").append(metaNode.get("language").asText()).append("\n");
                    }
                    if (metaNode.has("summary") && !metaNode.get("summary").asText().isBlank()) {
                        context.append("  Resumo: ").append(metaNode.get("summary").asText()).append("\n");
                    }
                    if (metaNode.has("keywords") && metaNode.get("keywords").isArray()) {
                        StringBuilder kw = new StringBuilder();
                        metaNode.get("keywords").forEach(k -> {
                            if (kw.length() > 0)
                                kw.append(", ");
                            kw.append(k.asText());
                        });
                        if (kw.length() > 0) {
                            context.append("  Palavras-chave: ").append(kw).append("\n");
                        }
                    }
                    if (metaNode.has("topics") && metaNode.get("topics").isArray()) {
                        StringBuilder tp = new StringBuilder();
                        metaNode.get("topics").forEach(t -> {
                            if (tp.length() > 0)
                                tp.append(", ");
                            tp.append(t.asText());
                        });
                        if (tp.length() > 0) {
                            context.append("  Tópicos: ").append(tp).append("\n");
                        }
                    }
                    if (metaNode.has("confidence")) {
                        context.append("  Confiança: ").append(metaNode.get("confidence").asDouble()).append("\n");
                    }
                    context.append("\n");
                } catch (Exception e) {
                    log.debug("Erro ao parsear metadata: {}", e.getMessage());
                }
            }

            context.append("CONTEÚDO:\n");
            context.append(chunk.getContent());
            context.append("\n\n");
        }

        return context.toString().trim();
    }

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

    private RetrievedChunkDto toRetrievedChunkDto(ChunkSearchResult chunk) {
        return RetrievedChunkDto.builder()
                .id(chunk.getId())
                .documentId(chunk.getDocumentId())
                .contentSnippet(truncate(chunk.getContent(), MAX_SNIPPET_LENGTH))
                .chunkIndex(chunk.getChunkIndex())
                .tokenCount(chunk.getTokenCount())
                .build();
    }

    private String truncate(String text, int maxLength) {
        if (text == null)
            return "";
        if (text.length() <= maxLength)
            return text;
        return text.substring(0, maxLength - 3) + "...";
    }
}
