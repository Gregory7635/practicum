package com.logistics.delivery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// @SpringBootApplication - "три в одном": включает автоконфигурацию Spring Boot,
// сканирование компонентов (@Component, @Service, @RestController) в этом пакете и подпакетах,
// и объявляет этот класс как конфигурационный.
@SpringBootApplication
public class DeliveryReviewApplication {
    public static void main(String[] args) {
        // Точка входа: поднимает встроенный веб-сервер (по умолчанию Tomcat) и весь контекст Spring.
        SpringApplication.run(DeliveryReviewApplication.class, args);
    }
}
