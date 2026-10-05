package com.logistics.delivery.llm;

import com.logistics.delivery.config.LlmProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты AiGateway: выбор провайдера по конфигурации, fallback, переключение на резервного, метрики.
 * Провайдеры замоканы - сеть не используется.
 */
class AiGatewayTest {

    private LlmClient openai;
    private LlmClient internal;
    private LlmProperties properties;
    private SimpleMeterRegistry meterRegistry;
    private LlmRequest request;

    @BeforeEach
    void setUp() {
        openai = mock(LlmClient.class);
        internal = mock(LlmClient.class);
        when(openai.providerName()).thenReturn("openai");
        when(internal.providerName()).thenReturn("internal");

        properties = new LlmProperties();
        meterRegistry = new SimpleMeterRegistry();
        request = LlmRequest.builder().prompt("test prompt").build();
    }

    private AiGateway gateway() {
        return new AiGateway(List.of(openai, internal), properties, meterRegistry);
    }

    @Test
    void generate_shouldUseActiveProviderOnly() {
        properties.setActiveProvider("internal");
        when(internal.generate(any(LlmRequest.class))).thenReturn(LlmResponse.success("from internal"));

        LlmResponse response = gateway().generate(request);

        assertThat(response.text()).isEqualTo("from internal");
        assertThat(response.isFallback()).isFalse();
        verify(openai, never()).generate(any(LlmRequest.class));
    }

    @Test
    void generate_shouldSwitchProvider_whenConfigurationChanges() {
        // Смена провайдера - только конфигурация, код Service/Gateway не меняется.
        when(openai.generate(any(LlmRequest.class))).thenReturn(LlmResponse.success("from openai"));

        properties.setActiveProvider("openai");

        assertThat(gateway().generate(request).text()).isEqualTo("from openai");
    }

    @Test
    void generate_shouldReturnSafeFallback_whenProviderFails() {
        properties.setActiveProvider("openai");
        when(openai.generate(any(LlmRequest.class))).thenThrow(new IllegalStateException("boom"));

        LlmResponse response = gateway().generate(request);

        assertThat(response.isFallback()).isTrue();
        // Технические детали исключения пользователю не показываются.
        assertThat(response.text()).doesNotContain("boom");
    }

    @Test
    void generate_shouldSwitchToFallbackProvider_whenActiveFailsAndFallbackConfigured() {
        properties.setActiveProvider("openai");
        properties.setFallbackProvider("internal");
        when(openai.generate(any(LlmRequest.class))).thenThrow(new IllegalStateException("boom"));
        when(internal.generate(any(LlmRequest.class))).thenReturn(LlmResponse.success("from internal"));

        LlmResponse response = gateway().generate(request);

        assertThat(response.isFallback()).isFalse();
        assertThat(response.text()).isEqualTo("from internal");
    }

    @Test
    void generate_shouldReturnSafeFallback_whenBothProvidersFail() {
        properties.setActiveProvider("openai");
        properties.setFallbackProvider("internal");
        when(openai.generate(any(LlmRequest.class))).thenThrow(new IllegalStateException("boom"));
        when(internal.generate(any(LlmRequest.class))).thenThrow(new IllegalStateException("boom too"));

        assertThat(gateway().generate(request).isFallback()).isTrue();
    }

    @Test
    void generate_shouldThrow_whenActiveProviderIsUnknown() {
        properties.setActiveProvider("no-such-provider");

        assertThrows(IllegalStateException.class, () -> gateway().generate(request));
    }

    @Test
    void generate_shouldCountSuccessAndErrorMetrics() {
        properties.setActiveProvider("openai");
        when(openai.generate(any(LlmRequest.class)))
                .thenReturn(LlmResponse.success("ok"))
                .thenThrow(new IllegalStateException("boom"));

        AiGateway gateway = gateway();
        gateway.generate(request);
        gateway.generate(request);

        assertThat(meterRegistry.counter("llm.requests", "provider", "openai", "status", "success").count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.counter("llm.requests", "provider", "openai", "status", "error").count())
                .isEqualTo(1.0);
    }
}
