package com.logistics.delivery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

/**
 * Типобезопасная привязка секции "llm" из application.yml.
 * Секреты (api-key) сюда попадают ТОЛЬКО через переменные окружения
 * (${OPENAI_API_KEY}) — в самом application.yml и в репозитории их нет.
 */
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    /** Провайдер, который сейчас активен: "openai" или "internal". */
    private String activeProvider; // читается из llm.active-provider в application.yml

    /**
     * Резервный провайдер: на него AiGateway переключается автоматически, если активный
     * провайдер недоступен. Пусто — резервирование выключено.
     */
    private String fallbackProvider; // llm.fallback-provider

    /** Настройки каждого провайдера по имени. */
    private Map<String, ProviderConfig> providers; // ключ - имя провайдера ("openai", "internal"), значение - его настройки

    public String getActiveProvider() {
        return activeProvider;
    }

    public void setActiveProvider(String activeProvider) {
        this.activeProvider = activeProvider;
    }

    public String getFallbackProvider() {
        return fallbackProvider;
    }

    public void setFallbackProvider(String fallbackProvider) {
        this.fallbackProvider = fallbackProvider;
    }

    public Map<String, ProviderConfig> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, ProviderConfig> providers) {
        this.providers = providers;
    }

    public static class ProviderConfig {
        private String baseUrl;               // адрес API провайдера, например https://api.openai.com
        private String apiKey;                // секрет, берётся из ${ENV_VAR}; у internal-провайдера может быть null
        private String model = "gpt-4o-mini";  // имя модели, которое передаётся во внешний API
        private Duration timeout = Duration.ofSeconds(5); // сколько ждать ответа, прежде чем считать вызов неудачным
        private int retries = 1;              // сколько раз повторить запрос при сбое, прежде чем сдаться
        private String chatPath = "/v1/chat/completions"; // путь Chat Completions относительно base-url (у OpenAI-совместимых API может отличаться)
        private boolean apiKeyRequired = true;            // false - провайдер работает без ключа (бесплатный free-провайдер)

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public int getRetries() {
            return retries;
        }

        public void setRetries(int retries) {
            this.retries = retries;
        }

        public String getChatPath() {
            return chatPath;
        }

        public void setChatPath(String chatPath) {
            this.chatPath = chatPath;
        }

        public boolean isApiKeyRequired() {
            return apiKeyRequired;
        }

        public void setApiKeyRequired(boolean apiKeyRequired) {
            this.apiKeyRequired = apiKeyRequired;
        }
    }
}
