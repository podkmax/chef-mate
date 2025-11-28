package com.chefmate.mapper;

import com.chefmate.dto.OrderDto;
import com.chefmate.model.Order;
import com.chefmate.model.OrderStatus;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", uses = OrderItemMapper.class, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OrderMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "status", expression = "java(mapStatus(dto))")
    Order toEntity(OrderDto dto);

    @Mapping(target = "status", expression = "java(entity != null && entity.status != null ? entity.status.name() : null)")
    OrderDto toDto(Order entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "status", expression = "java(mapStatus(dto, entity.status))")
    void updateEntityFromDto(OrderDto dto, @MappingTarget Order entity);

    default OrderStatus mapStatus(OrderDto dto) {
        if (dto == null || dto.status == null) {
            return OrderStatus.CREATED;
        }
        return OrderStatus.valueOf(dto.status);
    }

    default OrderStatus mapStatus(OrderDto dto, OrderStatus fallback) {
        if (dto == null || dto.status == null) {
            return fallback;
        }
        return OrderStatus.valueOf(dto.status);
    }

    @AfterMapping
    default void linkItems(@MappingTarget Order order) {
        if (order.items != null) {
            order.items.forEach(item -> item.order = order);
        }
    }
}
