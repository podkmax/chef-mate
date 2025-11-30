package com.chefmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record DishIngredientDto(
        Long id,
        @NotBlank String name,
        @NotNull BigDecimal qty,
        @NotNull java.util.UUID unitId,
        UnitDto unit,
        Boolean excludeForClient,
        java.util.UUID baseProductId) {
}
