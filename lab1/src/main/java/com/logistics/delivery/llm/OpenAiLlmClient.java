package com.logistics.delivery.llm;

import com.logistics.delivery.client.OpenAiApiClient;
import com.logistics.delivery.config.LlmProperties;
import org.springframework.stereotype.Component;

/**
 * Реализация LlmClient для внешнего провайдера OpenAI.
 * Сама не делает HTTP-вызовов — делегирует их в client-layer (OpenAiApiClient).
 * Это разделение позволяет тестировать LlmClient без сети (мокая ApiClient)
 * и держать HTTP-детали в одном месте.
 */
@Component
public class OpenAiLlmClient implements LlmClient {

    private final OpenAiApiClient apiClient;   // низкоуровневый HTTP-клиент (реальная сеть - только здесь)
    private final LlmProperties properties;     // конфигурация всех провайдеров из application.yml

    public OpenAiLlmClient(OpenAiApiClient apiClient, LlmProperties properties) {
        this.apiClient = apiClient;
        this.properties = properties;
    }

    @Override
    public String providerName() {
        return "openai"; // это имя должно совпадать с ключом в application.yml (llm.providers.openai)
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
        // Достаём настройки именно для openai (base-url, api-key, timeout, retries) из общей конфигурации.
        LlmProperties.ProviderConfig config = properties.getProviders().get("openai");
        // Делегируем реальный HTTP-вызов в client-layer, сами никакого RestTemplate не создаём.
        String text = apiClient.complete(
                config,
                request.getPrompt(),
                request.getMaxTokens(),
                request.getTemperature()
        );
        // Оборачиваем сырой текстовый ответ в единый формат LlmResponse, понятный AiGateway и Service.
        return LlmResponse.success(text);
    }
}
