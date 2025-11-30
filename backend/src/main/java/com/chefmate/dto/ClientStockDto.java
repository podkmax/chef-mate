package com.chefmate.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record ClientStockDto(
        UUID id,
        @NotNull UUID baseProductId,
        @NotNull BigDecimal qty,
        String unit,
        String baseProductName,
        Boolean isFreezable) {
}
