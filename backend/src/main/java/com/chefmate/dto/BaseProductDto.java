package com.chefmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class BaseProductDto {
    public UUID id;
    @NotBlank
    public String name;
    @NotBlank
    public String unit;
    @NotNull
    public Boolean isFreezable;
}
