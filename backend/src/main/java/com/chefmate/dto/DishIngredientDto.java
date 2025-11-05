package com.chefmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class DishIngredientDto {
    public Long id;
    @NotBlank
    public String name;
    @NotNull
    public BigDecimal qty;
    @NotNull
    public java.util.UUID unitId;
    public UnitDto unit;
    public Boolean excludeForClient;
    public java.util.UUID baseProductId;
}
