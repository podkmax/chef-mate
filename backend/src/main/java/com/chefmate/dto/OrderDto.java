package com.chefmate.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record OrderDto(
        Long id,
        @NotNull Long userId,
        @NotNull LocalDate targetDate,
        String status,
        String comment,
        @NotEmpty @Valid List<OrderItemDto> items) {
}

