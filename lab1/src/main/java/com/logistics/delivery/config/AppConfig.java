package com.logistics.delivery.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class AppConfig {

    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(5);

    /**
     * RestTemplate с явными timeout'ами (без них "зависший" провайдер держал бы поток бесконечно).
     * connectTimeout - сколько ждать установления соединения;
     * readTimeout - сколько ждать ответа; берётся из llm.providers.openai.timeout (application.yml),
     * а если там не задано - используется значение по умолчанию.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder, LlmProperties properties) {
        Duration readTimeout = DEFAULT_READ_TIMEOUT;
        if (properties.getProviders() != null) {
            LlmProperties.ProviderConfig openai = properties.getProviders().get("openai");
            if (openai != null && openai.getTimeout() != null) {
                readTimeout = openai.getTimeout();
            }
        }
        return builder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(readTimeout)
                .build();
    }
}
