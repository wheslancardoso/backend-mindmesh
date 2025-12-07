package com.mindmesh.backend.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Serviço responsável por transformar texto em embeddings vetoriais.
 * Suporta modo real (OpenAI) e modo mock (quando OPENAI_API_KEY não está
 * definida).
 * Também fornece métodos de IA para enriquecimento de metadados.
 */
@Slf4j
@Service
public class EmbeddingService {

    private static final String MODEL_NAME = "text-embedding-3-small";
    private static final String CHAT_MODEL_NAME = "gpt-4o-mini";
    private static final int VECTOR_SIZE = 1536;
    private static final int MAX_CHARACTERS = 32_000;
    private static final int MAX_CHAT_INPUT_CHARS = 4000;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final Duration TIMEOUT_DURATION = Duration.ofSeconds(30);
    private static final Duration INITIAL_BACKOFF = Duration.ofMillis(500);
    private static final double BACKOFF_MULTIPLIER = 2.0;

    private final EmbeddingModel embeddingModel;
    private final ChatLanguageModel chatModel;
    private final Retry retry;
    private final TimeLimiter timeLimiter;
    private final CircuitBreaker circuitBreaker;
    private final ExecutorService executor;
    private final boolean mockMode;

    public EmbeddingService() {
        String apiKey = System.getenv("OPENAI_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("⚠️  OPENAI_API_KEY não definida - EmbeddingService em MODO MOCK");
            this.mockMode = true;
            this.embeddingModel = null;
            this.chatModel = null;
            this.retry = null;
            this.timeLimiter = null;
            this.circuitBreaker = null;
            this.executor = null;
            return;
        }

        this.mockMode = false;

        this.embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(MODEL_NAME)
                .timeout(Duration.ofSeconds(60))
                .build();

        this.chatModel = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(CHAT_MODEL_NAME)
                .temperature(0.3)
                .timeout(Duration.ofSeconds(30))
                .build();

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(MAX_RETRY_ATTEMPTS)
                .intervalFunction(
                        attempt -> (long) (INITIAL_BACKOFF.toMillis() * Math.pow(BACKOFF_MULTIPLIER, attempt - 1)))
                .retryExceptions(Exception.class)
                .build();
        this.retry = Retry.of("embeddingRetry", retryConfig);

        retry.getEventPublisher()
                .onRetry(event -> log.warn(
                        "Tentativa {} de {} falhou. Aguardando {}ms. Erro: {}",
                        event.getNumberOfRetryAttempts(),
                        MAX_RETRY_ATTEMPTS,
                        event.getWaitInterval().toMillis(),
                        event.getLastThrowable().getMessage()))
                .onError(event -> log.error(
                        "Todas as {} tentativas falharam. Erro: {}",
                        event.getNumberOfRetryAttempts(),
                        event.getLastThrowable().getMessage()));

        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
                .timeoutDuration(TIMEOUT_DURATION)
                .cancelRunningFuture(true)
                .build();
        this.timeLimiter = TimeLimiter.of("embeddingTimeLimiter", timeLimiterConfig);

        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .slidingWindowSize(10)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .build();
        this.circuitBreaker = CircuitBreaker.of("embeddingCircuitBreaker", circuitBreakerConfig);
        circuitBreaker.transitionToDisabledState();

        this.executor = Executors.newCachedThreadPool();

        log.info("EmbeddingService REAL inicializado: {} + {} | Retry: {} | Timeout: {}s",
                MODEL_NAME, CHAT_MODEL_NAME, MAX_RETRY_ATTEMPTS, TIMEOUT_DURATION.getSeconds());
    }

    public boolean isMockMode() {
        return mockMode;
    }

    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            log.warn("Texto nulo ou vazio, retornando embedding vazio");
            return new float[0];
        }

        if (mockMode) {
            return embedMock(text);
        }

        return embedReal(text);
    }

    private float[] embedMock(String text) {
        float[] v = new float[VECTOR_SIZE];
        int seed = text.hashCode();
        Random r = new Random(seed);

        for (int i = 0; i < VECTOR_SIZE; i++) {
            v[i] = (r.nextFloat() * 2f) - 1f;
        }

        log.debug("Mock embedding gerado: {} chars, seed: {}", text.length(), seed);
        return v;
    }

    private float[] embedReal(String text) {
        String processedText = truncateIfNeeded(text);

        log.info("Gerando embedding real: {} chars", processedText.length());

        Supplier<float[]> embeddingSupplier = () -> callOpenAiApi(processedText);

        Supplier<float[]> decoratedSupplier = Retry.decorateSupplier(retry, () -> {
            try {
                CompletableFuture<float[]> future = CompletableFuture.supplyAsync(embeddingSupplier, executor);
                return timeLimiter.executeFutureSupplier(() -> future);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });

        try {
            float[] result = decoratedSupplier.get();
            log.info("Embedding gerado: {} dimensões", result.length);
            return result;

        } catch (Exception e) {
            log.error("Falha ao gerar embedding: {}", e.getMessage(), e);
            throw new RuntimeException("Falha ao gerar embedding: " + e.getMessage(), e);
        }
    }

    private float[] callOpenAiApi(String text) {
        Response<Embedding> response = embeddingModel.embed(text);
        return response.content().vector();
    }

    private String truncateIfNeeded(String text) {
        if (text.length() <= MAX_CHARACTERS) {
            return text;
        }

        log.warn("Texto excede {} chars ({}), truncando", MAX_CHARACTERS, text.length());

        String truncated = text.substring(0, MAX_CHARACTERS);

        int lastParagraph = truncated.lastIndexOf("\n\n");
        if (lastParagraph > MAX_CHARACTERS * 0.8) {
            return truncated.substring(0, lastParagraph).trim();
        }

        int lastSentence = Math.max(
                truncated.lastIndexOf(". "),
                Math.max(truncated.lastIndexOf("! "), truncated.lastIndexOf("? ")));
        if (lastSentence > MAX_CHARACTERS * 0.8) {
            return truncated.substring(0, lastSentence + 1).trim();
        }

        int lastSpace = truncated.lastIndexOf(" ");
        if (lastSpace > MAX_CHARACTERS * 0.9) {
            return truncated.substring(0, lastSpace).trim();
        }

        return truncated.trim();
    }

    // ==================== AI HELPER METHODS ====================

    /**
     * Gera um resumo curto do texto usando IA.
     * Nunca lança exceções - retorna string vazia em caso de falha.
     */
    public String summarize(String text) {
        if (mockMode) {
            log.debug("summarize() em modo mock");
            return "[MOCK] Resumo do documento gerado automaticamente.";
        }

        try {
            String input = truncateForChat(text);
            String prompt = "Gere um resumo curto (máximo 3 frases) do seguinte texto:\n\n" + input;
            String result = chatModel.generate(prompt);
            log.debug("summarize() concluído: {} chars", result.length());
            return result.trim();
        } catch (Exception e) {
            log.error("Erro ao gerar resumo: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Extrai palavras-chave do texto usando IA.
     * Retorna string CSV (5-10 palavras).
     */
    public String extractKeywords(String text) {
        if (mockMode) {
            log.debug("extractKeywords() em modo mock");
            return "documento, análise, texto, conteúdo, informação";
        }

        try {
            String input = truncateForChat(text);
            String prompt = "Liste de 5 a 10 palavras-chave do texto abaixo, separadas por vírgula. " +
                    "Retorne apenas as palavras, sem numeração ou explicação:\n\n" + input;
            String result = chatModel.generate(prompt);
            log.debug("extractKeywords() concluído: {}", result);
            return result.trim();
        } catch (Exception e) {
            log.error("Erro ao extrair keywords: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Extrai tópicos principais do texto usando IA.
     * Retorna string CSV.
     */
    public String extractTopics(String text) {
        if (mockMode) {
            log.debug("extractTopics() em modo mock");
            return "Análise de Documentos, Processamento de Texto";
        }

        try {
            String input = truncateForChat(text);
            String prompt = "Liste os tópicos principais deste documento, separados por vírgula. " +
                    "Retorne apenas os tópicos, sem numeração ou explicação:\n\n" + input;
            String result = chatModel.generate(prompt);
            log.debug("extractTopics() concluído: {}", result);
            return result.trim();
        } catch (Exception e) {
            log.error("Erro ao extrair tópicos: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Classifica o tipo do documento usando IA.
     */
    public String classifyDocument(String text) {
        if (mockMode) {
            log.debug("classifyDocument() em modo mock");
            return "documento genérico";
        }

        try {
            String input = truncateForChat(text);
            String prompt = "Classifique o tipo deste documento. Responda com apenas uma categoria entre: " +
                    "artigo, relatório, email, documentação técnica, tutorial, nota pessoal, ata de reunião, " +
                    "contrato, manual, apresentação, ou outro.\n\n" + input;
            String result = chatModel.generate(prompt);
            log.debug("classifyDocument() concluído: {}", result);
            return result.trim().toLowerCase();
        } catch (Exception e) {
            log.error("Erro ao classificar documento: {}", e.getMessage());
            return "desconhecido";
        }
    }

    /**
     * Trunca texto para chamadas de chat (limite menor que embeddings).
     */
    private String truncateForChat(String text) {
        if (text == null || text.length() <= MAX_CHAT_INPUT_CHARS) {
            return text;
        }
        return text.substring(0, MAX_CHAT_INPUT_CHARS);
    }
}
