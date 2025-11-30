package com.chefmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record BaseProductDto(
        UUID id,
        @NotBlank String name,
        @NotBlank String unit,
        @NotNull Boolean isFreezable) {
}
