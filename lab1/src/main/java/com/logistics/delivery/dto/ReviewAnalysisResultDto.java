package com.logistics.delivery.dto;

import java.util.List;

/**
 * Результат анализа отзыва: краткое резюме + список проблемных точек.
 * Это именно то, что Controller вернёт клиенту в теле HTTP-ответа (JSON).
 */
public record ReviewAnalysisResultDto(
        String orderId,                  // тот же orderId, что был во входящем запросе — чтобы клиент мог сопоставить ответ с запросом
        String summary,                  // краткое резюме отзыва в одном предложении (генерирует LLM)
        List<ProblemPointDto> problems   // список найденных проблем; может быть пустым списком, если проблем нет
) {
}
