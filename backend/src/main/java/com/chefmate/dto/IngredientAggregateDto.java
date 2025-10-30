package com.chefmate.dto;

import java.math.BigDecimal;

public class IngredientAggregateDto {
    public String name;
    public BigDecimal totalQty;
    public String unit;
    public BigDecimal stockQty;
    public BigDecimal requiredQty;
    public java.util.UUID baseProductId;
}

