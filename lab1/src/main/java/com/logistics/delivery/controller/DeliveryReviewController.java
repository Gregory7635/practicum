package com.logistics.delivery.controller;

import com.logistics.delivery.dto.DeliveryReviewDto;
import com.logistics.delivery.dto.ReviewAnalysisResultDto;
import com.logistics.delivery.service.DeliveryReviewAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller-layer.
 * Правило проекта: контроллер НЕ вызывает LLM и внешние API напрямую,
 * НЕ содержит бизнес-логики, только делегирует в Service и мапит HTTP-ответ.
 */
@RestController
public class DeliveryReviewController {

    private final DeliveryReviewAnalysisService analysisService;

    // Spring сам подставит сюда бин DeliveryReviewAnalysisService (Dependency Injection через конструктор).
    public DeliveryReviewController(DeliveryReviewAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    // @PostMapping связывает этот метод с HTTP POST-запросом на указанный путь.
    // @RequestBody говорит Spring автоматически преобразовать JSON из тела запроса в объект DeliveryReviewDto.
    @PostMapping("/api/delivery-reviews/analyze")
    public ResponseEntity<ReviewAnalysisResultDto> analyze(@RequestBody DeliveryReviewDto review) {
        // Вся работа делегирована в Service - контроллер не знает, что где-то внутри вызывается LLM.
        ReviewAnalysisResultDto result = analysisService.analyze(review);
        // Оборачиваем результат в ResponseEntity со статусом 200 OK и сериализуем обратно в JSON.
        return ResponseEntity.ok(result);
    }
}
