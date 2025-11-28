package com.chefmate.mapper;

import com.chefmate.dto.OrderItemDto;
import com.chefmate.model.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OrderItemMapper {

    @Mapping(target = "order", ignore = true)
    OrderItem toEntity(OrderItemDto dto);

    OrderItemDto toDto(OrderItem entity);
}
