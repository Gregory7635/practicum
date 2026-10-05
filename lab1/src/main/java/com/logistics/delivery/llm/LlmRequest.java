package com.logistics.delivery.llm;

/**
 * Единый формат запроса к LLM, не зависящий от конкретного провайдера.
 * Именно этот класс "видит" бизнес-логика (Service), а не SDK конкретного
 * провайдера (OpenAI, внутренняя модель и т.п.).
 *
 * Класс использует паттерн Builder — вместо длинного конструктора с
 * множеством параметров мы вызываем методы по цепочке:
 * LlmRequest.builder().prompt("...").maxTokens(200).build();
 * Это удобнее читать и не даёт перепутать порядок аргументов.
 */
public class LlmRequest {

    private final String prompt;      // текст запроса к модели (то, что мы хотим у неё спросить)
    private final int maxTokens;      // ограничение на длину ответа модели (защита от неконтролируемых расходов)
    private final double temperature; // "творческость" модели: чем ближе к 0, тем более предсказуемый и детерминированный ответ

    // Приватный конструктор — создать объект можно только через Builder ниже,
    // это гарантирует, что объект всегда собран правильно и является неизменяемым (immutable).
    private LlmRequest(Builder builder) {
        this.prompt = builder.prompt;
        this.maxTokens = builder.maxTokens;
        this.temperature = builder.temperature;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getPrompt() {
        return prompt;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public double getTemperature() {
        return temperature;
    }

    /** Builder — вспомогательный класс для пошагового конструирования LlmRequest. */
    public static class Builder {
        private String prompt;
        private int maxTokens = 300;     // значение по умолчанию, если явно не задано
        private double temperature = 0.3; // значение по умолчанию: невысокая температура для более стабильных ответов

        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this; // возвращаем this, чтобы можно было продолжить цепочку вызовов (.prompt(...).maxTokens(...))
        }

        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        // Финальный шаг цепочки: собирает все накопленные значения в готовый неизменяемый объект LlmRequest.
        public LlmRequest build() {
            return new LlmRequest(this);
        }
    }
}
