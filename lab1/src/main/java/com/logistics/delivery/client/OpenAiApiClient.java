package com.logistics.delivery.client;

import com.logistics.delivery.config.LlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Client-layer: единственное место в приложении, откуда выполняется
 * реальный HTTP-вызов к внешнему OpenAI API (Chat Completions) и к
 * OpenAI-совместимым API (например, бесплатному Pollinations для провайдера free).
 *
 * Правило проекта: любой внешний вызов идёт ТОЛЬКО отсюда, а не из
 * Controller, Service или напрямую из LlmClient-реализации.
 */
@Component
public class OpenAiApiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiApiClient.class);

    private final RestTemplate restTemplate;

    public OpenAiApiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Выполняет запрос к OpenAI с ретраями. Секреты (apiKey) никогда
     * не логируются, в лог попадает только факт вызова и его статус.
     */
    public String complete(LlmProperties.ProviderConfig config, String prompt, int maxTokens, double temperature) {
        boolean hasKey = config.getApiKey() != null && !config.getApiKey().isBlank();
        // Без ключа вызов заведомо бессмысленен - не тратим попытки, сразу сообщаем AiGateway об ошибке конфигурации.
        // Исключение - бесплатные OpenAI-совместимые провайдеры (api-key-required: false), им ключ не нужен.
        if (!hasKey && config.isApiKeyRequired()) {
            throw new IllegalStateException("OpenAI API key is not configured (env OPENAI_API_KEY)");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (hasKey) {
            headers.setBearerAuth(config.getApiKey()); // ключ пришёл из переменной окружения, не хардкод
        }

        // Тело запроса в формате OpenAI Chat Completions.
        Map<String, Object> body = Map.of(
                "model", config.getModel(),
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "max_tokens", maxTokens,
                "temperature", temperature
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        int attempts = 0;
        RestClientException lastError = null;

        // Цикл повторных попыток: от 1 до (retries + 1) попыток, пока не получим успешный ответ.
        while (attempts <= config.getRetries()) {
            attempts++;
            try {
                log.info("Calling OpenAI provider, attempt={}", attempts);
                Map<?, ?> response = restTemplate.postForObject(
                        config.getBaseUrl() + config.getChatPath(),
                        entity,
                        Map.class
                );
                String text = extractText(response);
                if (text != null) {
                    return text; // успех
                }
                // Ответ пришёл, но в нём нет текста - считаем ошибкой и уходим на следующую попытку.
                throw new RestClientException("Unexpected response structure from OpenAI");
            } catch (RestClientException e) {
                lastError = e;
                log.warn("OpenAI call failed on attempt={}: {}", attempts, e.getClass().getSimpleName());
            }
        }
        // Все попытки исчерпаны - пробрасываем исключение выше (его поймает AiGateway).
        throw new IllegalStateException("OpenAI provider unavailable after " + attempts + " attempts", lastError);
    }

    /** Достаёт choices[0].message.content из ответа Chat Completions; null, если структура неожиданная. */
    private String extractText(Map<?, ?> response) {
        if (response == null || !(response.get("choices") instanceof List<?> choices) || choices.isEmpty()) {
            return null;
        }
        if (!(choices.get(0) instanceof Map<?, ?> first) || !(first.get("message") instanceof Map<?, ?> message)) {
            return null;
        }
        Object content = message.get("content");
        return content == null ? null : content.toString();
    }
}
