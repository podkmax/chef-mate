package com.chefmate.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public class ClientStockDto {
    public UUID id;
    @NotNull
    public UUID baseProductId;
    @NotNull
    public BigDecimal qty;
    public String unit;
    public String baseProductName;
    public Boolean isFreezable;
}
