package com.logistics.delivery.llm;

import com.logistics.delivery.config.LlmProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AiGateway — единая точка входа в мир LLM для всего приложения.
 *
 * Задачи:
 *  1. Выбрать активного провайдера по конфигурации (llm.active-provider),
 *     не заставляя Service знать про конкретные реализации LlmClient.
 *  2. При сбое активного провайдера - автоматически переключиться на резервного
 *     (llm.fallback-provider, если задан), а если и он недоступен - вернуть безопасный
 *     fallback-ответ вместо падения запроса пользователя.
 *  3. Собрать метрики обращений (llm.requests: провайдер + success/error) для observability.
 *  4. Логировать факт обращения БЕЗ содержимого промпта и ответа
 *     (в промпте могут быть персональные данные клиента).
 *
 * Благодаря этому слою смена провайдера (например, openai -> internal)
 * — это правка одной строки в application.yml, а не изменение кода бизнес-логики.
 */
@Component
public class AiGateway {

    private static final Logger log = LoggerFactory.getLogger(AiGateway.class);

    private final Map<String, LlmClient> clients;   // все реализации LlmClient, доступ по имени провайдера
    private final LlmProperties properties;          // конфигурация из application.yml
    private final MeterRegistry meterRegistry;        // сборщик метрик (Micrometer)

    // Spring сам находит ВСЕ бины типа LlmClient (OpenAiLlmClient, InternalLlmClient) и передаёт списком.
    public AiGateway(List<LlmClient> clients, LlmProperties properties, MeterRegistry meterRegistry) {
        this.clients = clients.stream()
                .collect(Collectors.toMap(LlmClient::providerName, Function.identity()));
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    public LlmResponse generate(LlmRequest request) {
        String activeProvider = properties.getActiveProvider();
        LlmClient client = clients.get(activeProvider);

        if (client == null) {
            // Неизвестное имя в конфигурации - ошибка конфигурации, а не сбой сети: не "глотаем" её.
            log.error("Unknown LLM provider configured: {}", activeProvider);
            throw new IllegalStateException("Unknown LLM provider: " + activeProvider);
        }

        // 1. Основной провайдер.
        LlmResponse response = tryProvider(client, request);
        if (response != null) {
            return response;
        }

        // 2. Резервный провайдер (если настроен и отличается от основного).
        String fallbackName = properties.getFallbackProvider();
        if (fallbackName != null && !fallbackName.isBlank() && !fallbackName.equals(activeProvider)) {
            LlmClient fallbackClient = clients.get(fallbackName);
            if (fallbackClient == null) {
                log.error("Unknown fallback LLM provider configured: {}", fallbackName);
            } else {
                log.warn("Switching from provider={} to fallback provider={}", activeProvider, fallbackName);
                response = tryProvider(fallbackClient, request);
                if (response != null) {
                    return response;
                }
            }
        }

        // 3. Все провайдеры недоступны - безопасный ответ-заглушка.
        return safeFallback();
    }

    /** Вызывает одного провайдера; при любой ошибке считает метрику и возвращает null (а не бросает исключение). */
    private LlmResponse tryProvider(LlmClient client, LlmRequest request) {
        try {
            log.info("Sending request to LLM provider={}", client.providerName());
            LlmResponse response = client.generate(request);
            meterRegistry.counter("llm.requests", "provider", client.providerName(), "status", "success")
                    .increment();
            return response;
        } catch (Exception e) {
            // В лог - только тип исключения: сообщение может содержать детали запроса.
            log.warn("LLM provider={} failed: {}", client.providerName(), e.getClass().getSimpleName());
            meterRegistry.counter("llm.requests", "provider", client.providerName(), "status", "error")
                    .increment();
            return null;
        }
    }

    /**
     * Безопасный ответ на случай полного сбоя. Технические детали исключения пользователю
     * не показываем (могут раскрыть внутреннее устройство системы) - они есть в логе.
     */
    private LlmResponse safeFallback() {
        return LlmResponse.fallback("Не удалось проанализировать отзыв автоматически. "
                + "Обращение передано оператору для ручной обработки.");
    }
}
