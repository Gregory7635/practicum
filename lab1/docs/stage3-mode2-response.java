package com.logistics.delivery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final AiGateway aiGateway;
    private final ObjectMapper objectMapper;

    public DeliveryReviewAnalysisService(AiGateway aiGateway, ObjectMapper objectMapper) {
        this.aiGateway = aiGateway;
        this.objectMapper = objectMapper;
    }

    public ReviewAnalysisResultDto analyze(DeliveryReviewDto review) {
        validate(review);

        LlmRequest request = LlmRequest.builder()
                .prompt(buildPrompt(review))
                .maxTokens(400)
                .temperature(0.2) // низкая температура: нужен структурированный, а не творческий ответ
                .build();

        LlmResponse response = aiGateway.generate(request);

        if (response.isFallback()) {
            log.warn("Received fallback response for orderId={}", review.orderId());
            return new ReviewAnalysisResultDto(review.orderId(), response.text(), Collections.emptyList());
        }

        return parseResponse(review.orderId(), response.text());
    }

    private void validate(DeliveryReviewDto review) {
        if (review.reviewText() == null || review.reviewText().isBlank()) {
            throw new IllegalArgumentException("Review text is required");
        }
        if (review.orderId() == null || review.orderId().isBlank()) {
            throw new IllegalArgumentException("Order id is required");
        }
    }

    /**
     * Промпт формулирует задачу и явно требует строгий JSON-формат ответа,
     * чтобы результат можно было надёжно распарсить (а не полагаться на
     * свободный текст модели).
     */
    private String buildPrompt(DeliveryReviewDto review) {
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
    }

    /**
     * Парсинг ответа модели в строгий DTO. Ответ LLM — недоверенные данные:
     * при некорректном JSON не бросаем 500 клиенту, а возвращаем безопасный результат.
     */
    private ReviewAnalysisResultDto parseResponse(String orderId, String rawJson) {
        try {
            ParsedLlmAnalysis parsed = objectMapper.readValue(rawJson, ParsedLlmAnalysis.class);
            List<ProblemPointDto> problems = parsed.problems() == null
                    ? Collections.emptyList()
                    : parsed.problems().stream()
                        .map(p -> new ProblemPointDto(p.category(), p.description(), p.severity()))
                        .toList();
            return new ReviewAnalysisResultDto(orderId, parsed.summary(), problems);
        } catch (Exception e) {
            log.warn("Failed to parse LLM response as JSON for orderId={}: {}", orderId, e.getClass().getSimpleName());
            return new ReviewAnalysisResultDto(orderId,
                    "Не удалось автоматически разобрать ответ модели.",
                    Collections.emptyList());
        }
    }

    /** Внутренняя структура ровно под JSON-схему из промпта, для десериализации Jackson. */
    private record ParsedLlmAnalysis(String summary, List<ParsedProblem> problems) {
    }

    private record ParsedProblem(String category, String description, String severity) {
    }
}
