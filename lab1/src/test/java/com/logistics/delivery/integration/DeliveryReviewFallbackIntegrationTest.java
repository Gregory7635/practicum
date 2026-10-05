package com.logistics.delivery.integration;

import com.logistics.delivery.client.OpenAiApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Интеграционный тест сценария сбоя: активен провайдер openai, а внешний API "падает".
 * Клиент не должен получить 500 - AiGateway возвращает fallback, Service отдаёт пустой список проблем.
 */
@SpringBootTest(properties = {"llm.active-provider=openai", "llm.fallback-provider="})
@AutoConfigureMockMvc
class DeliveryReviewFallbackIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OpenAiApiClient openAiApiClient; // подменяем единственное место реального HTTP-вызова

    @Test
    void analyze_shouldReturn200WithFallback_whenProviderFails() throws Exception {
        when(openAiApiClient.complete(any(), anyString(), anyInt(), anyDouble()))
                .thenThrow(new IllegalStateException("provider unavailable"));

        String body = "{\"orderId\":\"ORD-9\",\"reviewText\":\"Посылка пришла повреждённой\",\"rating\":1}";

        mockMvc.perform(post("/api/delivery-reviews/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ORD-9"))
                .andExpect(jsonPath("$.summary").isNotEmpty())
                .andExpect(jsonPath("$.problems").isEmpty());
    }
}
