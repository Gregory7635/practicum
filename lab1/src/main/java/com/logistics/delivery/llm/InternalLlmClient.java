package com.logistics.delivery.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Внутренняя реализация LlmClient — заглушка вместо собственной/локальной модели.
 * В реальном проекте здесь мог бы быть вызов self-hosted модели во внутреннем
 * контуре компании (без передачи данных внешнему провайдеру).
 *
 * Заглушка возвращает ВАЛИДНЫЙ JSON в том же формате, что требует промпт
 * (summary + problems), поэтому вся цепочка Controller → Service → AiGateway → LlmClient
 * работает end-to-end без внешнего API и ключей. Список problems всегда пуст:
 * реального анализа заглушка не делает.
 */
@Component
public class InternalLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(InternalLlmClient.class);

    @Override
    public String providerName() {
        return "internal"; // должно совпадать с ключом llm.providers.internal в application.yml
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
        log.info("Generating response via internal (stub) provider");
        // request.getPrompt().length() показывает, что запрос реально дошёл до клиента (сам текст не логируем).
        String json = "{\"summary\":\"[internal-stub] Заглушка внутренней модели: реальный анализ не выполнялся. "
                + "Длина запроса: " + request.getPrompt().length() + " символов.\","
                + "\"problems\":[]}";
        return LlmResponse.success(json);
    }
}
