package com.chefmate.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderItemDto(
        @NotNull Long dishId,
        @NotNull @Min(1) Integer portions,
        String notes) {
}

