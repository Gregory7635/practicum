package com.logistics.delivery.llm;

/**
 * Единый формат ответа от LLM, не зависящий от провайдера.
 * isFallback = true означает, что это не настоящий ответ модели,
 * а безопасная заглушка, возвращённая после ошибки (см. AiGateway.fallback).
 */
public class LlmResponse {

    private final String text;        // текст ответа модели (или текст заглушки, если isFallback = true)
    private final boolean isFallback; // флаг: true - это аварийная заглушка, а не настоящий ответ модели

    // Приватный конструктор: извне объект создаётся только через фабричные методы success()/fallback() ниже,
    // это не даёт случайно перепутать обычный ответ с fallback-ответом.
    private LlmResponse(String text, boolean isFallback) {
        this.text = text;
        this.isFallback = isFallback;
    }

    // Фабричный метод для обычного успешного ответа модели.
    public static LlmResponse success(String text) {
        return new LlmResponse(text, false);
    }

    // Фабричный метод для безопасного ответа-заглушки при сбое провайдера.
    public static LlmResponse fallback(String text) {
        return new LlmResponse(text, true);
    }

    public String text() {
        return text;
    }

    public boolean isFallback() {
        return isFallback;
    }
}
