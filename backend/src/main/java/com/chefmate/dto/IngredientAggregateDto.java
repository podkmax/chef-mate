package com.chefmate.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record IngredientAggregateDto(
        String name,
        BigDecimal totalQty,
        UUID unitId,
        UnitDto unit,
        String unitShortName,
        BigDecimal stockQty,
        BigDecimal requiredQty,
        UUID baseProductId) {
}
