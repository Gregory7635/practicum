package com.logistics.delivery.dto;

/**
 * Единый формат ошибки REST API (HTTP 4xx/5xx).
 * Содержит только безопасное описание: без стектрейсов и деталей внутреннего устройства.
 */
public record ErrorResponseDto(
        String error,    // машинно-читаемый код, например BAD_REQUEST
        String message   // человекочитаемое пояснение для клиента API
) {
}
