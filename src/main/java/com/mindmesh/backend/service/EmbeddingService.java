package com.mindmesh.backend.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Serviço responsável por transformar texto em embeddings vetoriais.
 * Utiliza a API da OpenAI com modelo text-embedding-3-small.
 * 
 * Implementa resiliência com:
 * - Retry: 3 tentativas com backoff exponencial (300ms → 1.5s → 3s)
 * - Timeout: 6 segundos por chamada
 * - Circuit Breaker: Desabilitado por padrão (pronto para ativar)
 */
@Slf4j
@Service
public class EmbeddingService {

    private static final String MODEL_NAME = "text-embedding-3-small";

    /**
     * Limite aproximado de caracteres para 8k tokens.
     * Estimativa: 1 token ≈ 4 caracteres em inglês, ~3 em português.
     * Usando 32k caracteres como buffer seguro para ~8k tokens.
     */
    private static final int MAX_CHARACTERS = 32_000;

    // Configurações de resiliência
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final Duration TIMEOUT_DURATION = Duration.ofSeconds(6);
    private static final Duration INITIAL_BACKOFF = Duration.ofMillis(300);
    private static final double BACKOFF_MULTIPLIER = 2.5; // 300ms → 750ms → 1875ms ≈ 300→1.5s→3s

    private final EmbeddingModel embeddingModel;
    private final Retry retry;
    private final TimeLimiter timeLimiter;
    private final CircuitBreaker circuitBreaker;
    private final ExecutorService executor;

    public EmbeddingService(@Value("${openai.api.key}") String apiKey) {
        this.embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(MODEL_NAME)
                .build();

        // Configurar Retry com backoff exponencial
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(MAX_RETRY_ATTEMPTS)
                .intervalFunction(
                        attempt -> (long) (INITIAL_BACKOFF.toMillis() * Math.pow(BACKOFF_MULTIPLIER, attempt - 1)))
                .retryExceptions(Exception.class)
                .build();
        this.retry = Retry.of("embeddingRetry", retryConfig);

        // Registrar listeners para logging
        retry.getEventPublisher()
                .onRetry(event -> log.warn(
                        "Tentativa {} de {} falhou. Aguardando {}ms antes de retry. Erro: {}",
                        event.getNumberOfRetryAttempts(),
                        MAX_RETRY_ATTEMPTS,
                        event.getWaitInterval().toMillis(),
                        event.getLastThrowable().getMessage()))
                .onError(event -> log.error(
                        "Todas as {} tentativas falharam. Último erro: {}",
                        event.getNumberOfRetryAttempts(),
                        event.getLastThrowable().getMessage()));

        // Configurar Timeout
        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
                .timeoutDuration(TIMEOUT_DURATION)
                .cancelRunningFuture(true)
                .build();
        this.timeLimiter = TimeLimiter.of("embeddingTimeLimiter", timeLimiterConfig);

        // Configurar Circuit Breaker (DESABILITADO - pronto para ativar)
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50) // Abre após 50% de falhas
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .slidingWindowSize(10)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .build();
        this.circuitBreaker = CircuitBreaker.of("embeddingCircuitBreaker", circuitBreakerConfig);

        // DESABILITAR Circuit Breaker por padrão
        circuitBreaker.transitionToDisabledState();

        // Executor para operações assíncronas (timeout)
        this.executor = Executors.newCachedThreadPool();

        log.info(
                "EmbeddingService inicializado com modelo: {} | Retry: {} tentativas | Timeout: {}s | CircuitBreaker: DESABILITADO",
                MODEL_NAME, MAX_RETRY_ATTEMPTS, TIMEOUT_DURATION.getSeconds());
    }

    /**
     * Converte texto em um vetor de embeddings.
     * 
     * Resiliência aplicada:
     * - Retry: até 3 tentativas com backoff exponencial
     * - Timeout: 6 segundos máximo por tentativa
     *
     * @param text Texto a ser convertido
     * @return float[] com 1536 dimensões, ou array vazio se texto for nulo/vazio
     * @throws RuntimeException se todas as tentativas falharem
     */
    public float[] embed(String text) {
        // Validação: texto nulo ou vazio retorna embedding vazio
        if (text == null || text.isBlank()) {
            log.warn("Texto nulo ou vazio recebido para embedding, retornando array vazio");
            return new float[0];
        }

        // Truncar inteligentemente se ultrapassar limite de tokens (~8k)
        String processedText = truncateIfNeeded(text);

        log.info("Gerando embedding para texto com {} caracteres (original: {})",
                processedText.length(), text.length());

        // Supplier com a lógica de chamada à API
        Supplier<float[]> embeddingSupplier = () -> callOpenAiApi(processedText);

        // Aplicar decoradores: Retry → TimeLimiter
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
            log.info("Embedding gerado com sucesso: {} dimensões", result.length);
            return result;

        } catch (Exception e) {
            String errorMessage = String.format(
                    "Falha ao gerar embedding após %d tentativas. Texto: %d chars. Erro: %s",
                    MAX_RETRY_ATTEMPTS,
                    processedText.length(),
                    extractRootCause(e).getMessage());

            log.error(errorMessage, e);
            throw new RuntimeException(errorMessage, extractRootCause(e));
        }
    }

    /**
     * Chamada real à API da OpenAI (sem decoradores).
     */
    private float[] callOpenAiApi(String text) {
        Response<Embedding> response = embeddingModel.embed(text);
        Embedding embedding = response.content();
        return embedding.vector();
    }

    /**
     * Extrai a causa raiz de uma exceção encadeada.
     */
    private Throwable extractRootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    /**
     * Trunca o texto inteligentemente se ultrapassar o limite de tokens.
     * Tenta cortar em limites de sentença ou parágrafo para manter contexto.
     *
     * @param text Texto original
     * @return Texto truncado ou original se dentro do limite
     */
    private String truncateIfNeeded(String text) {
        if (text.length() <= MAX_CHARACTERS) {
            return text;
        }

        log.warn("Texto excede limite de {} caracteres ({}), truncando inteligentemente",
                MAX_CHARACTERS, text.length());

        String truncated = text.substring(0, MAX_CHARACTERS);

        // Tentar cortar no último parágrafo completo
        int lastParagraph = truncated.lastIndexOf("\n\n");
        if (lastParagraph > MAX_CHARACTERS * 0.8) {
            return truncated.substring(0, lastParagraph).trim();
        }

        // Tentar cortar na última sentença completa
        int lastSentence = Math.max(
                truncated.lastIndexOf(". "),
                Math.max(truncated.lastIndexOf("! "), truncated.lastIndexOf("? ")));
        if (lastSentence > MAX_CHARACTERS * 0.8) {
            return truncated.substring(0, lastSentence + 1).trim();
        }

        // Fallback: cortar no último espaço
        int lastSpace = truncated.lastIndexOf(" ");
        if (lastSpace > MAX_CHARACTERS * 0.9) {
            return truncated.substring(0, lastSpace).trim();
        }

        return truncated.trim();
    }

    /**
     * Converte uma lista de Double para array de float.
     * Útil para compatibilidade com APIs que retornam List<Double>.
     *
     * @param list Lista de valores Double
     * @return Array de float correspondente
     */
    private float[] toFloatArray(List<Double> list) {
        if (list == null || list.isEmpty()) {
            return new float[0];
        }

        float[] result = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Double value = list.get(i);
            result[i] = value != null ? value.floatValue() : 0.0f;
        }
        return result;
    }

    /**
     * Ativa o Circuit Breaker para proteção contra falhas em cascata.
     * Use quando a API estiver instável.
     */
    public void enableCircuitBreaker() {
        circuitBreaker.transitionToClosedState();
        log.info("Circuit Breaker ATIVADO para EmbeddingService");
    }

    /**
     * Desativa o Circuit Breaker.
     */
    public void disableCircuitBreaker() {
        circuitBreaker.transitionToDisabledState();
        log.info("Circuit Breaker DESATIVADO para EmbeddingService");
    }
}
