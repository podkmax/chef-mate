package com.chefmate.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class DishDto {
    public Long id;
    @NotBlank
    public String category;
    @NotBlank
    public String title;
    public String description;
    public Boolean active;
    @NotEmpty
    @Valid
    public List<DishIngredientDto> ingredients;
}

