package com.chefmate.service;

import com.chefmate.dto.CookOrderDishDto;
import com.chefmate.dto.OrderDto;
import com.chefmate.dto.OrderItemDto;
import com.chefmate.dto.IngredientAggregateDto;
import com.chefmate.model.*;
import com.chefmate.repo.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.function.Function;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Service
public class OrderService {
    private final OrderRepository orderRepo;
    private final DishRepository dishRepo;
    private final CookNotificationService cookNotificationService;
    private final ClientNotificationService clientNotificationService;
    private final ClientStockRepository clientStockRepository;
    private final BaseProductRepository baseProductRepository;
    private final UnitService unitService;

    public OrderService(
            OrderRepository orderRepo,
            DishRepository dishRepo,
            CookNotificationService cookNotificationService,
            ClientNotificationService clientNotificationService,
            ClientStockRepository clientStockRepository,
            BaseProductRepository baseProductRepository,
            UnitService unitService) {
        this.orderRepo = orderRepo;
        this.dishRepo = dishRepo;
        this.cookNotificationService = cookNotificationService;
        this.clientNotificationService = clientNotificationService;
        this.clientStockRepository = clientStockRepository;
        this.baseProductRepository = baseProductRepository;
        this.unitService = unitService;
    }

    @Transactional
    public OrderDto createOrder(OrderDto dto) {
        validateOrderPayload(dto);
        Order order = fromDto(dto);
        order.status = OrderStatus.CREATED;
        OffsetDateTime now = OffsetDateTime.now();
        order.createdAt = now;
        order.updatedAt = now;
        orderRepo.save(order);
        Map<Long, Dish> dishMap = loadDishMap(order);
        List<CookOrderDishDto> dishSummaries = buildDishSummaries(order, dishMap);
        List<IngredientAggregateDto> cookIngredients = aggregateIngredients(order, dishMap, false);
        cookNotificationService.notifyNewOrder(order, dishSummaries, cookIngredients);
        return toDto(order);
    }

    @Transactional
    public OrderDto updateOrder(Long id, OrderDto dto) {
        validateOrderPayload(dto);
        Order entity = orderRepo.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Заказ не найден"));
        entity.userId = dto.userId;
        entity.targetDate = dto.targetDate;
        entity.status = dto.status != null ? OrderStatus.valueOf(dto.status) : entity.status;
        entity.comment = dto.comment;
        List<OrderItem> items = entity.items != null ? entity.items : new ArrayList<>();
        items.clear();
        for (OrderItemDto i : dto.items) {
            OrderItem item = new OrderItem();
            item.order = entity;
            item.dishId = i.dishId;
            item.portions = i.portions;
            item.notes = i.notes;
            items.add(item);
        }
        entity.items = items;
        entity.updatedAt = OffsetDateTime.now();
        orderRepo.save(entity);
        return toDto(entity);
    }
    @Transactional(readOnly = true)
    public List<OrderDto> findAll() {
        return orderRepo.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }
    @Transactional(readOnly = true)
    public OrderDto findById(Long id) {
        return orderRepo.findById(id).map(this::toDto).orElse(null);
    }
    @Transactional(readOnly = true)
    public List<OrderDto> findByDate(LocalDate date) {
        return orderRepo.findByTargetDate(date).stream().map(this::toDto).collect(Collectors.toList());
    }
    // Агрегация ингредиентов (excludeForClient = false если client==true)
    @Transactional(readOnly = true)
    public List<IngredientAggregateDto> aggregateIngredients(Long orderId, boolean forClient) {
        Order order = orderRepo.findById(orderId).orElseThrow();
        Map<Long, Dish> dishMap = loadDishMap(order);
        return aggregateIngredients(order, dishMap, forClient);
    }

    private Map<Long, Dish> loadDishMap(Order order) {
        if (order == null || order.items == null || order.items.isEmpty()) {
            return Map.of();
        }
        Set<Long> dishIds = order.items.stream()
                .map(item -> item.dishId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (dishIds.isEmpty()) {
            return Map.of();
        }
        return dishRepo.findAllById(dishIds).stream()
                .collect(Collectors.toMap(d -> d.id, Function.identity()));
    }

    private List<CookOrderDishDto> buildDishSummaries(Order order, Map<Long, Dish> dishMap) {
        if (order.items == null || order.items.isEmpty()) {
            return List.of();
        }
        List<CookOrderDishDto> result = new ArrayList<>();
        for (OrderItem item : order.items) {
            CookOrderDishDto dto = new CookOrderDishDto();
            dto.dishId = item.dishId;
            Dish dish = dishMap.get(item.dishId);
            dto.name = dish != null ? dish.title : ("Блюдо #" + item.dishId);
            dto.portions = item.portions != null ? item.portions : 1;
            result.add(dto);
        }
        return result;
    }

    private List<IngredientAggregateDto> aggregateIngredients(Order order, Map<Long, Dish> dishMap, boolean forClient) {
        if (order == null || order.items == null || order.items.isEmpty()) {
            return List.of();
        }
        Map<Long, Dish> effectiveDishMap = dishMap != null ? dishMap : loadDishMap(order);
        if (forClient) {
            Map<String, IngredientAggregateDto> aggr = new LinkedHashMap<>();
            for (OrderItem item : order.items) {
                Dish dish = effectiveDishMap.get(item.dishId);
                if (dish == null || dish.ingredients == null) {
                    continue;
                }
                for (DishIngredient ingr : dish.ingredients) {
                    if (Boolean.TRUE.equals(ingr.excludeForClient)) {
                        continue;
                    }
                    BaseProduct baseProduct = ingr.baseProduct != null
                            ? ingr.baseProduct
                            : baseProductRepository.findByNameIgnoreCase(ingr.name).orElse(null);
                    if (baseProduct == null) {
                        continue;
                    }
                    Unit unit = ingr.unit != null
                            ? ingr.unit
                            : unitService.resolveUnitOrDefault(baseProduct.unit);
                    String unitShort = resolveUnitShortName(unit, baseProduct);
                    BigDecimal q = calculateQuantity(ingr, item);
                    String displayName = baseProduct.name != null ? baseProduct.name : ingr.name;
                    String key = normalizeKey(displayName) + "|" + (unit != null ? unit.id : "default");
                    aggr.compute(key, (mapKey, value) -> {
                        if (value == null) {
                            IngredientAggregateDto dto = new IngredientAggregateDto();
                            dto.name = displayName;
                            dto.totalQty = q;
                            dto.requiredQty = q;
                            dto.unitId = unit != null ? unit.id : null;
                            dto.unit = unit != null ? unitService.toDto(unit) : null;
                            dto.unitShortName = unitShort;
                            dto.baseProductId = baseProduct.id;
                            return dto;
                        } else {
                            value.totalQty = value.totalQty.add(q);
                            value.requiredQty = value.totalQty;
                            if (value.unitShortName == null || value.unitShortName.isBlank()) {
                                value.unitShortName = unitShort;
                            }
                            return value;
                        }
                    });
                }
            }
            applyStockAdjustments(order, aggr.values(), true);
            return new ArrayList<>(aggr.values());
        } else {
            Map<String, IngredientAggregateDto> aggr = new LinkedHashMap<>();
            for (OrderItem item : order.items) {
                Dish dish = effectiveDishMap.get(item.dishId);
                if (dish == null || dish.ingredients == null) {
                    continue;
                }
                for (DishIngredient ingr : dish.ingredients) {
                    Unit unit = ingr.unit != null ? ingr.unit : unitService.getDefaultUnit();
                    String unitShort = resolveUnitShortName(unit, ingr.baseProduct);
                    BigDecimal q = calculateQuantity(ingr, item);
                    String name = ingr.name != null && !ingr.name.isBlank()
                            ? ingr.name
                            : (ingr.baseProduct != null ? ingr.baseProduct.name : "Ингредиент");
                    String key = normalizeKey(name) + "|" + (unit != null ? unit.id : "default");
                    aggr.compute(key, (k, value) -> {
                        if (value == null) {
                            IngredientAggregateDto dto = new IngredientAggregateDto();
                            dto.name = name;
                            dto.totalQty = q;
                            dto.unitId = unit != null ? unit.id : null;
                            dto.unit = unit != null ? unitService.toDto(unit) : null;
                            dto.unitShortName = unitShort;
                            dto.baseProductId = ingr.baseProduct != null ? ingr.baseProduct.id : null;
                            return dto;
                        } else {
                            value.totalQty = value.totalQty.add(q);
                            if (value.unitShortName == null || value.unitShortName.isBlank()) {
                                value.unitShortName = unitShort;
                            }
                            return value;
                        }
                    });
                }
            }
            return new ArrayList<>(aggr.values());
        }
    }

    private BigDecimal calculateQuantity(DishIngredient ingredient, OrderItem item) {
        BigDecimal baseQty = ingredient.qty != null ? ingredient.qty : BigDecimal.ZERO;
        int portions = item.portions != null ? item.portions : 1;
        return baseQty.multiply(BigDecimal.valueOf(portions));
    }

    private String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String resolveUnitShortName(Unit unit, BaseProduct baseProduct) {
        if (unit != null && unit.shortName != null && !unit.shortName.isBlank()) {
            return unit.shortName;
        }
        if (baseProduct != null && baseProduct.unit != null && !baseProduct.unit.isBlank()) {
            return unitService.normalizeShortName(baseProduct.unit);
        }
        return null;
    }

    private void applyStockAdjustments(Order order, Collection<IngredientAggregateDto> items, boolean reduceByStock) {
        if (order.userId == null || items.isEmpty()) {
            return;
        }
        List<ClientStock> stockItems = clientStockRepository.findByUserId(order.userId);
        Map<UUID, ClientStock> stockMap = stockItems.stream()
                .collect(Collectors.toMap(cs -> cs.baseProduct.id, cs -> cs, (a, b) -> a, LinkedHashMap::new));
        for (IngredientAggregateDto dto : items) {
            UUID baseProductId = dto.baseProductId != null ? dto.baseProductId : resolveBaseProductId(dto.name);
            if (baseProductId == null) {
                dto.stockQty = BigDecimal.ZERO;
                dto.requiredQty = dto.totalQty;
                continue;
            }
            ClientStock stock = stockMap.get(baseProductId);
            if (stock == null) {
                dto.stockQty = BigDecimal.ZERO;
                dto.requiredQty = dto.totalQty;
                continue;
            }
            BigDecimal available = stock.qty != null ? stock.qty : BigDecimal.ZERO;
            dto.stockQty = available;
            if (reduceByStock) {
                BigDecimal required = dto.totalQty != null ? dto.totalQty.subtract(available) : BigDecimal.ZERO;
                dto.requiredQty = required.max(BigDecimal.ZERO);
            } else {
                dto.requiredQty = dto.totalQty;
            }
        }
    }
    @Transactional
    public OrderDto confirmOrder(Long id) {
        Order order = orderRepo.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Заказ не найден"));
        if (order.status == OrderStatus.CONFIRMED) {
            return toDto(order);
        }
        order.status = OrderStatus.CONFIRMED;
        order.updatedAt = OffsetDateTime.now();
        orderRepo.save(order);
        Map<Long, Dish> dishMap = loadDishMap(order);
        List<IngredientAggregateDto> ingredients = aggregateIngredients(order, dishMap, true);
        adjustClientStock(order, ingredients);
        clientNotificationService.notifyOrderConfirmed(order, ingredients);
        return toDto(order);
    }
    private void adjustClientStock(Order order, List<IngredientAggregateDto> ingredients) {
        if (order.userId == null) {
            return;
        }
        List<ClientStock> stockItems = clientStockRepository.findByUserId(order.userId);
        Map<UUID, ClientStock> stockMap = stockItems.stream()
                .collect(Collectors.toMap(cs -> cs.baseProduct.id, cs -> cs));
        for (IngredientAggregateDto dto : ingredients) {
            UUID baseProductId = dto.baseProductId != null ? dto.baseProductId : resolveBaseProductId(dto.name);
            if (baseProductId == null) {
                continue;
            }
            ClientStock stock = stockMap.get(baseProductId);
            if (stock == null) {
                continue;
            }
            BigDecimal current = stock.qty != null ? stock.qty : BigDecimal.ZERO;
            BigDecimal required = dto.totalQty != null ? dto.totalQty : BigDecimal.ZERO;
            BigDecimal newQty = current.subtract(required);
            stock.qty = newQty.max(BigDecimal.ZERO);
            clientStockRepository.save(stock);
        }
    }

    private UUID resolveBaseProductId(String name) {
        if (name == null) {
            return null;
        }
        return baseProductRepository.findByNameIgnoreCase(name).map(bp -> bp.id).orElse(null);
    }
    @Transactional
    public OrderDto cancelOrder(Long id) {
        Order order = orderRepo.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Заказ не найден"));
        order.status = OrderStatus.CANCELLED;
        order.updatedAt = OffsetDateTime.now();
        orderRepo.save(order);
        return toDto(order);
    }
    private Order fromDto(OrderDto dto) {
        Order entity = new Order();
        entity.userId = dto.userId;
        entity.targetDate = dto.targetDate;
        entity.comment = dto.comment;
        entity.status = dto.status != null ? OrderStatus.valueOf(dto.status) : OrderStatus.CREATED;
        List<OrderItem> items = new ArrayList<>();
        if (dto.items != null) {
            items = dto.items.stream().map(i -> {
                OrderItem item = new OrderItem();
                item.dishId = i.dishId;
                item.portions = i.portions;
                item.notes = i.notes;
                item.order = entity;
                return item;
            }).collect(Collectors.toList());
        }
        entity.items = items;
        return entity;
    }
    private OrderDto toDto(Order entity) {
        OrderDto dto = new OrderDto();
        dto.id = entity.id;
        dto.userId = entity.userId;
        dto.targetDate = entity.targetDate;
        dto.status = entity.status != null ? entity.status.name() : null;
        dto.comment = entity.comment;
        if (entity.items != null)
            dto.items = entity.items.stream().map(i -> {
                OrderItemDto d = new OrderItemDto();
                d.dishId = i.dishId;
                d.portions = i.portions;
                d.notes = i.notes;
                return d;
            }).collect(Collectors.toList());
        return dto;
    }

    private void validateOrderPayload(OrderDto dto) {
        if (dto == null || dto.items == null || dto.items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Заказ должен содержать хотя бы одну позицию.");
        }
    }
}
