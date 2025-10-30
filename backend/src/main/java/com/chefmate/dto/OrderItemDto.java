package com.chefmate.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class OrderItemDto {
    @NotNull
    public Long dishId;
    @NotNull
    @Min(1)
    public Integer portions;
    public String notes;
}


