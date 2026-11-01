package com.logistics.delivery.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Objects;

@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class AppConfig {

    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(5);

    /**
     * RestTemplate с явными timeout'ами (без них "зависший" провайдер держал бы поток бесконечно).
     * connectTimeout - сколько ждать установления соединения;
     * readTimeout - сколько ждать ответа; RestTemplate общий для всех внешних провайдеров, поэтому берётся
     * наибольший timeout из llm.providers.*.timeout (application.yml): бесплатной модели нужно больше времени,
     * чем OpenAI. Если нигде не задано - используется значение по умолчанию.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder, LlmProperties properties) {
        Duration readTimeout = DEFAULT_READ_TIMEOUT;
        if (properties.getProviders() != null) {
            readTimeout = properties.getProviders().values().stream()
                    .map(LlmProperties.ProviderConfig::getTimeout)
                    .filter(Objects::nonNull)
                    .max(Duration::compareTo)
                    .orElse(DEFAULT_READ_TIMEOUT);
        }
        return builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(readTimeout)
                .build();
    }
}
