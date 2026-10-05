package com.logistics.delivery.domain;

/**
 * Доменная модель проблемной точки доставки.
 * Не путать с ProblemPointDto: DTO — контракт наружу (JSON API),
 * domain-модель — внутреннее представление, которое может развиваться
 * независимо от контракта (например, обрастать полями для аналитики).
 */
public record DeliveryProblem(
        ProblemCategory category,   // категория проблемы как enum (типобезопасно, в отличие от строки в DTO)
        String description,         // текстовое описание конкретного случая
        Severity severity           // серьёзность как enum
) {
    // Enum ограничивает возможные значения строго заданным набором -
    // компилятор не даст присвоить категорию, которой нет в списке.
    public enum ProblemCategory {
        DELAY, DAMAGED_PACKAGE, COURIER_BEHAVIOR, WRONG_ADDRESS, LOST_ITEM, OTHER
    }

    public enum Severity {
        LOW, MEDIUM, HIGH
    }
}
