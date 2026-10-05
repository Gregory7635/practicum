package com.logistics.delivery.llm;

/**
 * Абстракция над конкретным провайдером LLM.
 *
 * Бизнес-логика (Service) никогда не работает с OpenAI SDK, RestTemplate
 * к внешнему API и т.п. напрямую — только через этот интерфейс.
 * Это и есть защита от vendor lock: чтобы сменить провайдера, достаточно
 * добавить новую реализацию и переключить конфигурацию, не трогая Service.
 */
public interface LlmClient {

    /**
     * Уникальное имя провайдера, используется AiGateway для маршрутизации
     * (должно совпадать со значением llm.active-provider в application.yml).
     */
    String providerName(); // например "openai" или "internal"

    /**
     * Выполнить запрос к модели и получить ответ.
     * Реализация отвечает за собственные timeouts и retries на уровне HTTP-клиента.
     */
    LlmResponse generate(LlmRequest request); // принимает generic-запрос, отдаёт generic-ответ - без привязки к конкретному провайдеру
}
