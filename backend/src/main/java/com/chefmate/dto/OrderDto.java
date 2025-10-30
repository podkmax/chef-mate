package com.chefmate.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public class OrderDto {
    public Long id;
    @NotNull
    public Long userId;
    @NotNull
    public LocalDate targetDate;
    public String status;
    public String comment;
    @NotEmpty
    @Valid
    public List<OrderItemDto> items;
}


