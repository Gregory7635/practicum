// ЭТАП 4: AI-сгенерированный прототип "как есть" (ДО разбора и исправления).
// Типичный результат, если просто попросить AI: "напиши REST-эндпоинт,
// который анализирует отзыв клиента о доставке через OpenAI".
// Этот код НЕ используется в проекте - он приведён только для разбора ошибок.

package com.logistics.delivery.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
public class DeliveryReviewController {

    @Value("${openai.api.key}")
    private String apiKey;

    @PostMapping("/review/analyze")
    public String analyzeReview(@RequestBody String reviewText) {
        // прямой вызов LLM из контроллера
        RestTemplate restTemplate = new RestTemplate();
        String prompt = "Найди проблемы в отзыве клиента о доставке: " + reviewText;

        Map<String, String> request = Map.of("prompt", prompt, "key", apiKey);

        return restTemplate.postForObject(
                "https://api.openai.com/v1/completions",
                request,
                String.class
        );
    }
}
