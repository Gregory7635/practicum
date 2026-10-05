package com.logistics.delivery.controller;

import com.logistics.delivery.dto.ErrorResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Единое место преобразования исключений в HTTP-ответы.
 * Только HTTP-маппинг, бизнес-логики здесь нет (правило слоя controller).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Ошибка валидации входных данных (пустой отзыв, нет orderId, слишком длинный текст...) -> 400, а не 500.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponseDto("BAD_REQUEST", e.getMessage()));
    }

    // Ошибка конфигурации (например, неизвестный провайдер) -> 500 без технических деталей в теле ответа.
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponseDto> handleServerError(IllegalStateException e) {
        log.error("Internal configuration error: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponseDto("INTERNAL_ERROR", "Internal server error"));
    }
}
