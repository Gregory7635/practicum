package com.logistics.delivery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.delivery.domain.DeliveryProblem;
import com.logistics.delivery.dto.DeliveryReviewDto;
import com.logistics.delivery.dto.ProblemPointDto;
import com.logistics.delivery.dto.ReviewAnalysisResultDto;
import com.logistics.delivery.llm.AiGateway;
import com.logistics.delivery.llm.LlmRequest;
import com.logistics.delivery.llm.LlmResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Service-layer: вся бизнес-логика анализа отзыва клиента о доставке.
 *
 * Правило проекта (см. README-AI.md): бизнес-логика находится только здесь.
 * Controller не знает про LLM вообще, а AiGateway/LlmClient не знают
 * про предметную область "доставка" — только про generic prompt/response.
 */
@Service
public class DeliveryReviewAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryReviewAnalysisService.class);

    /** Ограничение длины отзыва: защита от слишком дорогих запросов (токены) и мусорного ввода. */
    static final int MAX_REVIEW_LENGTH = 2000;

    private final AiGateway aiGateway;         // единственная точка выхода к LLM (см. пакет llm)
    private final ObjectMapper objectMapper;    // Jackson-инструмент для превращения JSON-строки в Java-объект и обратно

    public DeliveryReviewAnalysisService(AiGateway aiGateway, ObjectMapper objectMapper) {
        this.aiGateway = aiGateway;
        this.objectMapper = objectMapper;
    }

    public ReviewAnalysisResultDto analyze(DeliveryReviewDto review) {
        // Шаг 1: проверяем, что входные данные вообще имеет смысл отправлять в LLM.
        validate(review);

        // Шаг 2: строим запрос к модели - собираем промпт и параметры генерации.
        LlmRequest request = LlmRequest.builder()
                .prompt(buildPrompt(review))
                .maxTokens(400)
                .temperature(0.2) // низкая температура: нужен структурированный, а не творческий ответ
                .build();

        // Шаг 3: отправляем запрос через AiGateway (какой именно провайдер ответит - решает конфигурация, не мы).
        LlmResponse response = aiGateway.generate(request);

        // Шаг 4: если AiGateway вернул fallback (была ошибка провайдера) - НЕ пытаемся парсить его как JSON,
        // а сразу возвращаем клиенту понятное сообщение с пустым списком проблем.
        if (response.isFallback()) {
            log.warn("Received fallback response for orderId={}", review.orderId());
            return new ReviewAnalysisResultDto(review.orderId(), response.text(), Collections.emptyList());
        }

        // Шаг 5: в штатном случае - разбираем текстовый ответ модели (ожидаем JSON) в структурированный DTO.
        return parseResponse(review.orderId(), response.text());
    }

    // Валидация "на входе": не даём некорректным данным уйти в промпт и потратить токены зря.
    private void validate(DeliveryReviewDto review) {
        if (review == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (review.reviewText() == null || review.reviewText().isBlank()) {
            throw new IllegalArgumentException("Review text is required");
        }
        if (review.orderId() == null || review.orderId().isBlank()) {
            throw new IllegalArgumentException("Order id is required");
        }
        if (review.reviewText().length() > MAX_REVIEW_LENGTH) {
            throw new IllegalArgumentException("Review text is too long (max " + MAX_REVIEW_LENGTH + " characters)");
        }
        if (review.rating() != null && (review.rating() < 1 || review.rating() > 5)) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
    }

    /**
     * Промпт формулирует задачу и явно требует строгий JSON-формат ответа,
     * чтобы результат можно было надёжно распарсить (а не полагаться на
     * свободный текст модели).
     */
    private String buildPrompt(DeliveryReviewDto review) {
        // Текстовый блок (Java text block, ограничен """) - многострочная строка без ручной склейки через +.
        // %s внутри - место подстановки значений через .formatted(...) в конце.
        return """
                Проанализируй отзыв клиента о доставке заказа и выдели проблемные точки.
                Отвечай СТРОГО в формате JSON без пояснений, по схеме:
                {
                  "summary": "краткое резюме отзыва в одном предложении",
                  "problems": [
                    {"category": "одно из: DELAY, DAMAGED_PACKAGE, COURIER_BEHAVIOR, WRONG_ADDRESS, LOST_ITEM, OTHER",
                     "description": "краткое описание проблемы",
                     "severity": "LOW, MEDIUM или HIGH"}
                  ]
                }
                Не добавляй проблемы, не подтверждённые текстом отзыва.
                Если проблем нет, верни пустой список problems.

                Текст отзыва: "%s"
                Оценка клиента (1-5, может отсутствовать): %s
                """.formatted(review.reviewText(), review.rating() == null ? "не указана" : review.rating());
        // .formatted() подставляет review.reviewText() вместо первого %s и оценку вместо второго %s.
    }

    /**
     * Парсинг ответа модели в строгий DTO. Ответ LLM — недоверенные данные:
     * при некорректном JSON не бросаем 500 клиенту, а возвращаем безопасный результат.
     */
    private ReviewAnalysisResultDto parseResponse(String orderId, String rawText) {
        try {
            // Jackson по описанию record ParsedLlmAnalysis сам сопоставляет поля JSON с полями Java-объекта.
            ParsedLlmAnalysis parsed = objectMapper.readValue(stripCodeFence(rawText), ParsedLlmAnalysis.class);
            // Превращаем внутренние ParsedProblem в публичные ProblemPointDto (внутренний формат парсинга
            // и внешний контракт API можно менять независимо). Категория и серьёзность нормализуются:
            // модель могла вернуть значение вне списка - тогда подставляем безопасное по умолчанию.
            List<ProblemPointDto> problems = parsed.problems() == null
                    ? Collections.emptyList()
                    : parsed.problems().stream()
                        .map(p -> new ProblemPointDto(
                                normalizeCategory(p.category()).name(),
                                p.description(),
                                normalizeSeverity(p.severity()).name()))
                        .toList();
            return new ReviewAnalysisResultDto(orderId, parsed.summary(), problems);
        } catch (Exception e) {
            // Модель могла вернуть невалидный JSON (например, добавила пояснение текстом) -
            // ловим ЛЮБОЕ исключение парсинга и отдаём безопасный результат вместо падения запроса.
            log.warn("Failed to parse LLM response as JSON for orderId={}: {}", orderId, e.getClass().getSimpleName());
            return new ReviewAnalysisResultDto(orderId,
                    "Не удалось автоматически разобрать ответ модели.",
                    Collections.emptyList());
        }
    }

    /** Модели часто оборачивают JSON в markdown-блок ```json ... ``` - снимаем обёртку перед парсингом. */
    private String stripCodeFence(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                text = text.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return text;
    }

    // Приводим строку от модели к enum из domain; неизвестное значение -> OTHER / MEDIUM.
    private DeliveryProblem.ProblemCategory normalizeCategory(String value) {
        try {
            return DeliveryProblem.ProblemCategory.valueOf(value.trim().toUpperCase());
        } catch (Exception e) { // null, пустая строка или значение вне списка
            return DeliveryProblem.ProblemCategory.OTHER;
        }
    }

    private DeliveryProblem.Severity normalizeSeverity(String value) {
        try {
            return DeliveryProblem.Severity.valueOf(value.trim().toUpperCase());
        } catch (Exception e) {
            return DeliveryProblem.Severity.MEDIUM;
        }
    }

    /** Внутренняя структура ровно под JSON-схему из промпта, для десериализации Jackson. */
    // private record - виден только внутри этого класса, наружу (в контроллер, в другие сервисы) не "утекает".
    private record ParsedLlmAnalysis(String summary, List<ParsedProblem> problems) {
    }

    private record ParsedProblem(String category, String description, String severity) {
    }
}
