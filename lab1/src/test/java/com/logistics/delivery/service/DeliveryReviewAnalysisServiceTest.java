package com.logistics.delivery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.delivery.dto.DeliveryReviewDto;
import com.logistics.delivery.dto.ReviewAnalysisResultDto;
import com.logistics.delivery.llm.AiGateway;
import com.logistics.delivery.llm.LlmRequest;
import com.logistics.delivery.llm.LlmResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты service-layer. AiGateway замокан — тест не выполняет
 * реальных сетевых вызовов и не зависит от доступности LLM-провайдера.
 */
@ExtendWith(MockitoExtension.class) // подключает поддержку аннотации @Mock от Mockito в JUnit 5
class DeliveryReviewAnalysisServiceTest {

    @Mock
    private AiGateway aiGateway; // Mockito создаст "поддельный" AiGateway - реального обращения к LLM не будет

    private DeliveryReviewAnalysisService service; // тестируемый объект (System Under Test)

    // Выполняется перед КАЖДЫМ тестом - гарантирует, что тесты не влияют друг на друга.
    @BeforeEach
    void setUp() {
        service = new DeliveryReviewAnalysisService(aiGateway, new ObjectMapper());
    }

    // Каждый тест построен по схеме Arrange-Act-Assert:
    // Arrange (подготовка) - задаём, что должен вернуть замоканный aiGateway.generate(...);
    // Act (действие)       - вызываем service.analyze(review);
    // Assert (проверка)    - сверяем результат с ожидаемым через assertThat(...).
    @Test
    void analyze_shouldReturnParsedProblems_whenLlmReturnsValidJson() {
        // Arrange: "притворяемся", что LLM вернула вот такой корректный JSON.
        String validJson = """
                {
                  "summary": "Клиент недоволен задержкой доставки",
                  "problems": [
                    {"category": "DELAY", "description": "Доставка задержана на 3 дня", "severity": "HIGH"}
                  ]
                }
                """;
        // any(LlmRequest.class) означает "при вызове с любым аргументом такого типа".
        when(aiGateway.generate(any(LlmRequest.class))).thenReturn(LlmResponse.success(validJson));

        // Act
        DeliveryReviewDto review = new DeliveryReviewDto("ORD-1", "Курьер приехал на 3 дня позже обещанного", 2);
        ReviewAnalysisResultDto result = service.analyze(review);

        // Assert: проверяем, что JSON распарсился именно так, как ожидалось.
        assertThat(result.orderId()).isEqualTo("ORD-1");
        assertThat(result.summary()).contains("задержк");
        assertThat(result.problems()).hasSize(1);
        assertThat(result.problems().get(0).category()).isEqualTo("DELAY");
        assertThat(result.problems().get(0).severity()).isEqualTo("HIGH");
    }

    @Test
    void analyze_shouldReturnEmptyProblemsSafely_whenLlmReturnsInvalidJson() {
        // Arrange: LLM "ведёт себя плохо" и вместо JSON возвращает обычный текст.
        when(aiGateway.generate(any(LlmRequest.class))).thenReturn(LlmResponse.success("это не JSON, а свободный текст"));

        // Act
        DeliveryReviewDto review = new DeliveryReviewDto("ORD-2", "Всё хорошо, спасибо", 5);
        ReviewAnalysisResultDto result = service.analyze(review);

        // Assert: сервис не должен упасть с исключением - должен вернуть пустой список проблем и непустое summary.
        assertThat(result.problems()).isEmpty();
        assertThat(result.summary()).isNotBlank();
    }

    @Test
    void analyze_shouldReturnFallbackResult_whenGatewayReturnsFallback() {
        // Arrange: имитируем ситуацию, когда сам AiGateway не смог достучаться до провайдера
        // и вернул безопасную заглушку (см. AiGateway.fallback()).
        when(aiGateway.generate(any(LlmRequest.class)))
                .thenReturn(LlmResponse.fallback("Сервис временно недоступен"));

        // Act
        DeliveryReviewDto review = new DeliveryReviewDto("ORD-3", "Посылка пришла повреждённой", 1);
        ReviewAnalysisResultDto result = service.analyze(review);

        // Assert: сервис должен передать текст fallback-а клиенту как есть, не пытаясь парсить его как JSON.
        assertThat(result.problems()).isEmpty();
        assertThat(result.summary()).isEqualTo("Сервис временно недоступен");
    }

    @Test
    void analyze_shouldThrow_whenReviewTextIsBlank() {
        // Проверяем валидацию: пустой (только из пробелов) текст отзыва должен быть отклонён
        // ДО обращения к LLM - иначе мы впустую потратили бы токены на пустой запрос.
        DeliveryReviewDto invalidReview = new DeliveryReviewDto("ORD-4", "   ", null);

        assertThrows(IllegalArgumentException.class, () -> service.analyze(invalidReview));
    }

    @Test
    void analyze_shouldThrow_whenOrderIdIsMissing() {
        // Аналогично - без orderId нельзя будет сопоставить результат анализа с исходным заказом.
        DeliveryReviewDto invalidReview = new DeliveryReviewDto(null, "Отзыв без ID заказа", null);

        assertThrows(IllegalArgumentException.class, () -> service.analyze(invalidReview));
    }

    @Test
    void analyze_shouldNormalizeUnknownCategoryAndSeverity_whenLlmReturnsValuesOutsideTheList() {
        // Модель - недоверенный источник: категория/серьёзность могут быть вне допустимого набора.
        String json = """
                {"summary": "Проблема",
                 "problems": [{"category": "WEIRD_CATEGORY", "description": "что-то", "severity": "CRITICAL"}]}
                """;
        when(aiGateway.generate(any(LlmRequest.class))).thenReturn(LlmResponse.success(json));

        ReviewAnalysisResultDto result = service.analyze(new DeliveryReviewDto("ORD-5", "Что-то пошло не так", 2));

        assertThat(result.problems()).hasSize(1);
        assertThat(result.problems().get(0).category()).isEqualTo("OTHER");
        assertThat(result.problems().get(0).severity()).isEqualTo("MEDIUM");
    }

    @Test
    void analyze_shouldParseJson_whenLlmWrapsItInMarkdownCodeFence() {
        String fenced = "```json\n{\"summary\": \"Ок\", \"problems\": []}\n```";
        when(aiGateway.generate(any(LlmRequest.class))).thenReturn(LlmResponse.success(fenced));

        ReviewAnalysisResultDto result = service.analyze(new DeliveryReviewDto("ORD-6", "Всё хорошо", 5));

        assertThat(result.summary()).isEqualTo("Ок");
        assertThat(result.problems()).isEmpty();
    }

    @Test
    void analyze_shouldThrow_whenReviewIsTooLong() {
        String tooLong = "а".repeat(DeliveryReviewAnalysisService.MAX_REVIEW_LENGTH + 1);

        assertThrows(IllegalArgumentException.class,
                () -> service.analyze(new DeliveryReviewDto("ORD-7", tooLong, 3)));
    }

    @Test
    void analyze_shouldThrow_whenRatingIsOutOfRange() {
        assertThrows(IllegalArgumentException.class,
                () -> service.analyze(new DeliveryReviewDto("ORD-8", "Нормальный отзыв", 6)));
    }

    @Test
    void analyze_shouldThrow_whenRequestIsNull() {
        assertThrows(IllegalArgumentException.class, () -> service.analyze(null));
    }
}
