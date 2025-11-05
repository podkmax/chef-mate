package com.chefmate.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class IngredientAggregateDto {
    public String name;
    public BigDecimal totalQty;
    public UUID unitId;
    public UnitDto unit;
    public BigDecimal stockQty;
    public BigDecimal requiredQty;
    public UUID baseProductId;
}
