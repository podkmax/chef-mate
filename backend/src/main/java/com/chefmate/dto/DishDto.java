package com.chefmate.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record DishDto(
        Long id,
        @NotBlank String category,
        @NotBlank String title,
        String description,
        Boolean active,
        @NotEmpty @Valid List<DishIngredientDto> ingredients) {
}
