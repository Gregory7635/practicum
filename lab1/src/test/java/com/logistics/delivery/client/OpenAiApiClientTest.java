package com.logistics.delivery.client;

import com.logistics.delivery.config.LlmProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Тесты client-layer без реальной сети: HTTP-сервер OpenAI эмулируется MockRestServiceServer.
 * Ключ ниже - заведомо фейковое значение, не настоящий секрет.
 */
class OpenAiApiClientTest {

    private static final String FAKE_API_KEY = "fake-key-for-tests";
    private static final String URL = "http://openai.test/v1/chat/completions";
    private static final String OK_BODY = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"hello\"}}]}";

    private MockRestServiceServer server;
    private OpenAiApiClient client;
    private LlmProperties.ProviderConfig config;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        client = new OpenAiApiClient(restTemplate);

        config = new LlmProperties.ProviderConfig();
        config.setBaseUrl("http://openai.test");
        config.setApiKey(FAKE_API_KEY);
        config.setRetries(1);
    }

    @Test
    void complete_shouldSendChatCompletionsRequestAndReturnMessageContent() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + FAKE_API_KEY))
                .andExpect(jsonPath("$.model").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.max_tokens").value(100))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        String text = client.complete(config, "prompt", 100, 0.2);

        assertThat(text).isEqualTo("hello");
        server.verify();
    }

    @Test
    void complete_shouldRetryAndSucceed_whenFirstAttemptFails() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        server.expect(requestTo(URL)).andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        assertThat(client.complete(config, "prompt", 100, 0.2)).isEqualTo("hello");
        server.verify();
    }

    @Test
    void complete_shouldThrow_whenAllAttemptsFail() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThrows(IllegalStateException.class, () -> client.complete(config, "prompt", 100, 0.2));
        server.verify();
    }

    @Test
    void complete_shouldThrowWithoutCallingApi_whenApiKeyIsMissing() {
        config.setApiKey("");

        assertThrows(IllegalStateException.class, () -> client.complete(config, "prompt", 100, 0.2));
        server.verify(); // ни одного HTTP-запроса не ожидалось и не было
    }
}
