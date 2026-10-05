package com.logistics.delivery.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Интеграционный тест: поднимается весь Spring-контекст, проходит вся цепочка
 * Controller → Service → AiGateway → InternalLlmClient (заглушка, без сети и без ключей).
 */
@SpringBootTest(properties = "llm.active-provider=internal")
@AutoConfigureMockMvc
class DeliveryReviewApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void analyze_shouldReturn200AndStubAnalysis_forValidRequest() throws Exception {
        String body = "{\"orderId\":\"ORD-123\",\"reviewText\":\"Курьер приехал на 3 дня позже\",\"rating\":2}";

        mockMvc.perform(post("/api/delivery-reviews/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ORD-123"))
                .andExpect(jsonPath("$.summary").value(containsString("[internal-stub]")))
                .andExpect(jsonPath("$.problems").isEmpty());
    }

    @Test
    void analyze_shouldReturn400_forBlankReviewText() throws Exception {
        String body = "{\"orderId\":\"ORD-124\",\"reviewText\":\"   \",\"rating\":3}";

        mockMvc.perform(post("/api/delivery-reviews/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }
}
