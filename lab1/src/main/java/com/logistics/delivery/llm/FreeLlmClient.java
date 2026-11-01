package com.logistics.delivery.llm;

import com.logistics.delivery.client.OpenAiApiClient;
import com.logistics.delivery.config.LlmProperties;
import org.springframework.stereotype.Component;

/**
 * Реализация LlmClient для бесплатной нейросети Pollinations (https://pollinations.ai).
 * API совместимо с OpenAI Chat Completions и работает без ключа, поэтому HTTP-вызов
 * делегируется тому же client-layer (OpenAiApiClient), что и у OpenAiLlmClient.
 *
 * Ограничение бесплатного режима: примерно 1 ответ в 45 секунд с одного IP.
 * При превышении лимита провайдер отвечает ошибкой, и AiGateway переключается
 * на резервного провайдера (llm.fallback-provider).
 */
@Component
public class FreeLlmClient implements LlmClient {

    /**
     * Бесплатная модель «рассуждает» перед ответом и тратит на это часть токенов,
     * поэтому лимит из запроса поднимаем до этого значения, иначе JSON может оборваться.
     */
    private static final int MIN_MAX_TOKENS = 2000;

    private final OpenAiApiClient apiClient;
    private final LlmProperties properties;

    public FreeLlmClient(OpenAiApiClient apiClient, LlmProperties properties) {
        this.apiClient = apiClient;
        this.properties = properties;
    }

    @Override
    public String providerName() {
        return "free"; // должно совпадать с ключом llm.providers.free в application.yml
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
        LlmProperties.ProviderConfig config = properties.getProviders().get("free");
        String text = apiClient.complete(
                config,
                request.getPrompt(),
                Math.max(request.getMaxTokens(), MIN_MAX_TOKENS),
                request.getTemperature()
        );
        return LlmResponse.success(text);
    }
}
