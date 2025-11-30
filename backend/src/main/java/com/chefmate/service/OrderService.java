package com.chefmate.service;

import com.chefmate.dto.CookOrderDishDto;
import com.chefmate.dto.IngredientAggregateDto;
import com.chefmate.dto.OrderDto;
import com.chefmate.mapper.OrderMapper;
import com.chefmate.model.BaseProduct;
import com.chefmate.model.ClientStock;
import com.chefmate.model.Dish;
import com.chefmate.model.DishIngredient;
import com.chefmate.model.Order;
import com.chefmate.model.OrderItem;
import com.chefmate.model.OrderStatus;
import com.chefmate.model.Unit;
import com.chefmate.repo.BaseProductRepository;
import com.chefmate.repo.ClientStockRepository;
import com.chefmate.repo.DishRepository;
import com.chefmate.repo.OrderRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {
    private static final String ORDER_NOT_FOUND_MESSAGE = "Заказ не найден";
    private static final String EMPTY_ORDER_ERROR = "Заказ должен содержать хотя бы одну позицию.";

    private final OrderRepository orderRepo;
    private final DishRepository dishRepo;
    private final CookNotificationService cookNotificationService;
    private final ClientNotificationService clientNotificationService;
    private final ClientStockRepository clientStockRepository;
    private final BaseProductRepository baseProductRepository;
    private final UnitService unitService;
    private final OrderMapper orderMapper;

    public OrderService(
            OrderRepository orderRepo,
            DishRepository dishRepo,
            CookNotificationService cookNotificationService,
            ClientNotificationService clientNotificationService,
            ClientStockRepository clientStockRepository,
            BaseProductRepository baseProductRepository,
            UnitService unitService,
            OrderMapper orderMapper) {
        this.orderRepo = orderRepo;
        this.dishRepo = dishRepo;
        this.cookNotificationService = cookNotificationService;
        this.clientNotificationService = clientNotificationService;
        this.clientStockRepository = clientStockRepository;
        this.baseProductRepository = baseProductRepository;
        this.unitService = unitService;
        this.orderMapper = orderMapper;
    }

    @Transactional
    public OrderDto createOrder(OrderDto dto) {
        validateOrderPayload(dto);
        Order order = orderMapper.toEntity(dto);
        OffsetDateTime now = OffsetDateTime.now();
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order.setStatus(OrderStatus.CREATED);
        orderRepo.save(order);

        Map<Long, Dish> dishMap = loadDishMap(order);
        List<CookOrderDishDto> dishSummaries = buildDishSummaries(order, dishMap);
        List<IngredientAggregateDto> cookIngredients = aggregateIngredients(order, dishMap, false);
        cookNotificationService.notifyNewOrder(order, dishSummaries, cookIngredients);
        return orderMapper.toDto(order);
    }

    @Transactional
    public OrderDto updateOrder(Long id, OrderDto dto) {
        validateOrderPayload(dto);
        Order entity = getOrderOrThrow(id);
        orderMapper.updateEntityFromDto(dto, entity);
        entity.setUpdatedAt(OffsetDateTime.now());
        orderRepo.save(entity);
        return orderMapper.toDto(entity);
    }

    @Transactional(readOnly = true)
    public List<OrderDto> findAll() {
        return orderRepo.findAll().stream().map(orderMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public OrderDto findById(Long id) {
        return orderRepo.findById(id).map(orderMapper::toDto).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<OrderDto> findByDate(LocalDate date) {
        return orderRepo.findByTargetDate(date).stream().map(orderMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<IngredientAggregateDto> aggregateIngredients(Long orderId, boolean forClient) {
        Order order = getOrderOrThrow(orderId);
        Map<Long, Dish> dishMap = loadDishMap(order);
        return aggregateIngredients(order, dishMap, forClient);
    }

    private Map<Long, Dish> loadDishMap(Order order) {
        List<OrderItem> items = order != null ? order.getItems() : null;
        if (items == null || items.isEmpty()) {
            return Map.of();
        }
        Set<Long> dishIds = items.stream()
                .map(OrderItem::getDishId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (dishIds.isEmpty()) {
            return Map.of();
        }
        return dishRepo.findAllById(dishIds).stream()
                .collect(Collectors.toMap(Dish::getId, Function.identity()));
    }

    private List<CookOrderDishDto> buildDishSummaries(Order order, Map<Long, Dish> dishMap) {
        List<OrderItem> items = order.getItems();
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<CookOrderDishDto> result = new ArrayList<>(items.size());
        for (OrderItem item : items) {
            Dish dish = dishMap.get(item.getDishId());
            String name = dish != null ? dish.getTitle() : "Блюдо #" + item.getDishId();
            int portions = item.getPortions() != null ? item.getPortions() : 1;
            result.add(new CookOrderDishDto(item.getDishId(), name, portions));
        }
        return result;
    }

    private List<IngredientAggregateDto> aggregateIngredients(Order order, Map<Long, Dish> dishMap, boolean forClient) {
        List<OrderItem> items = order.getItems();
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        Map<Long, Dish> effectiveDishMap = dishMap != null ? dishMap : loadDishMap(order);
        List<IngredientAggregateDto> aggregates = forClient
                ? aggregateIngredientsForClient(items, effectiveDishMap)
                : aggregateIngredientsForCook(items, effectiveDishMap);
        return forClient ? applyStockAdjustments(order, aggregates, true) : aggregates;
    }

    private List<IngredientAggregateDto> aggregateIngredientsForClient(List<OrderItem> items, Map<Long, Dish> dishMap) {
        Map<String, IngredientAggregateDto> aggr = new LinkedHashMap<>();
        for (OrderItem item : items) {
            Dish dish = dishMap.get(item.getDishId());
            if (dish == null || dish.getIngredients() == null) {
                continue;
            }
            for (DishIngredient ingredient : dish.getIngredients()) {
                if (Boolean.TRUE.equals(ingredient.getExcludeForClient())) {
                    continue;
                }
                BaseProduct baseProduct = ingredient.getBaseProduct() != null
                        ? ingredient.getBaseProduct()
                        : baseProductRepository.findByNameIgnoreCase(ingredient.getName()).orElse(null);
                if (baseProduct == null) {
                    continue;
                }
                Unit unit = ingredient.getUnit() != null
                        ? ingredient.getUnit()
                        : unitService.resolveUnitOrDefault(baseProduct.getUnit());
                String unitShort = resolveUnitShortName(unit, baseProduct);
                BigDecimal quantity = calculateQuantity(ingredient, item);
                String displayName = baseProduct.getName() != null ? baseProduct.getName() : ingredient.getName();
                String key = normalizeKey(displayName) + "|" + (unit != null ? unit.getId() : "default");
                aggr.compute(key, (mapKey, current) -> {
                    if (current == null) {
                        return new IngredientAggregateDto(
                                displayName,
                                quantity,
                                unit != null ? unit.getId() : null,
                                unit != null ? unitService.toDto(unit) : null,
                                unitShort,
                                null,
                                quantity,
                                baseProduct.getId());
                    }
                    BigDecimal updatedTotal = orZero(current.totalQty()).add(quantity);
                    BigDecimal updatedRequired = updatedTotal;
                    String updatedShort =
                            (current.unitShortName() == null || current.unitShortName().isBlank()) ? unitShort : current.unitShortName();
                    return new IngredientAggregateDto(
                            current.name(),
                            updatedTotal,
                            current.unitId(),
                            current.unit(),
                            updatedShort,
                            current.stockQty(),
                            updatedRequired,
                            current.baseProductId());
                });
            }
        }
        return new ArrayList<>(aggr.values());
    }

    private List<IngredientAggregateDto> aggregateIngredientsForCook(List<OrderItem> items, Map<Long, Dish> dishMap) {
        Map<String, IngredientAggregateDto> aggr = new LinkedHashMap<>();
        for (OrderItem item : items) {
            Dish dish = dishMap.get(item.getDishId());
            if (dish == null || dish.getIngredients() == null) {
                continue;
            }
            for (DishIngredient ingredient : dish.getIngredients()) {
                Unit unit = ingredient.getUnit() != null ? ingredient.getUnit() : unitService.getDefaultUnit();
                BaseProduct baseProduct = ingredient.getBaseProduct();
                String unitShort = resolveUnitShortName(unit, baseProduct);
                BigDecimal quantity = calculateQuantity(ingredient, item);
                String name = ingredient.getName() != null && !ingredient.getName().isBlank()
                        ? ingredient.getName()
                        : (baseProduct != null ? baseProduct.getName() : "Ингредиент");
                String key = normalizeKey(name) + "|" + (unit != null ? unit.getId() : "default");
                aggr.compute(key, (mapKey, current) -> {
                    if (current == null) {
                        return new IngredientAggregateDto(
                                name,
                                quantity,
                                unit != null ? unit.getId() : null,
                                unit != null ? unitService.toDto(unit) : null,
                                unitShort,
                                null,
                                null,
                                baseProduct != null ? baseProduct.getId() : null);
                    }
                    BigDecimal updatedTotal = orZero(current.totalQty()).add(quantity);
                    String updatedShort =
                            (current.unitShortName() == null || current.unitShortName().isBlank()) ? unitShort : current.unitShortName();
                    return new IngredientAggregateDto(
                            current.name(),
                            updatedTotal,
                            current.unitId(),
                            current.unit(),
                            updatedShort,
                            current.stockQty(),
                            current.requiredQty(),
                            current.baseProductId());
                });
            }
        }
        return new ArrayList<>(aggr.values());
    }

    private BigDecimal calculateQuantity(DishIngredient ingredient, OrderItem item) {
        BigDecimal baseQty = ingredient.getQty() != null ? ingredient.getQty() : BigDecimal.ZERO;
        int portions = item.getPortions() != null ? item.getPortions() : 1;
        return baseQty.multiply(BigDecimal.valueOf(portions));
    }

    private String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String resolveUnitShortName(Unit unit, BaseProduct baseProduct) {
        if (unit != null && unit.getShortName() != null && !unit.getShortName().isBlank()) {
            return unit.getShortName();
        }
        if (baseProduct != null && baseProduct.getUnit() != null && !baseProduct.getUnit().isBlank()) {
            return unitService.normalizeShortName(baseProduct.getUnit());
        }
        return null;
    }

    private List<IngredientAggregateDto> applyStockAdjustments(
            Order order,
            List<IngredientAggregateDto> items,
            boolean reduceByStock) {
        if (order.getUserId() == null || items.isEmpty()) {
            return items;
        }
        List<ClientStock> stockItems = clientStockRepository.findByUserId(order.getUserId());
        if (stockItems == null || stockItems.isEmpty()) {
            return items;
        }
        Map<UUID, ClientStock> stockMap = stockItems.stream()
                .filter(cs -> cs.getBaseProduct() != null && cs.getBaseProduct().getId() != null)
                .collect(Collectors.toMap(
                        cs -> cs.getBaseProduct().getId(),
                        Function.identity(),
                        (a, b) -> a,
                        LinkedHashMap::new));
        List<IngredientAggregateDto> adjusted = new ArrayList<>(items.size());
        for (IngredientAggregateDto item : items) {
            UUID baseProductId = item.baseProductId() != null ? item.baseProductId() : resolveBaseProductId(item.name());
            BigDecimal total = orZero(item.totalQty());
            BigDecimal stockQty = BigDecimal.ZERO;
            BigDecimal requiredQty = total;
            if (baseProductId != null) {
                ClientStock stock = stockMap.get(baseProductId);
                if (stock != null) {
                    stockQty = orZero(stock.getQty());
                    if (reduceByStock) {
                        requiredQty = total.subtract(stockQty).max(BigDecimal.ZERO);
                    }
                }
            }
            adjusted.add(new IngredientAggregateDto(
                    item.name(),
                    total,
                    item.unitId(),
                    item.unit(),
                    item.unitShortName(),
                    stockQty,
                    requiredQty,
                    baseProductId));
        }
        return adjusted;
    }

    private BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    @Transactional
    public OrderDto confirmOrder(Long id) {
        Order order = getOrderOrThrow(id);
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            return orderMapper.toDto(order);
        }
        order.setStatus(OrderStatus.CONFIRMED);
        order.setUpdatedAt(OffsetDateTime.now());
        orderRepo.save(order);
        Map<Long, Dish> dishMap = loadDishMap(order);
        List<IngredientAggregateDto> ingredients = aggregateIngredients(order, dishMap, true);
        adjustClientStock(order, ingredients);
        clientNotificationService.notifyOrderConfirmed(order, ingredients);
        return orderMapper.toDto(order);
    }

    private void adjustClientStock(Order order, List<IngredientAggregateDto> ingredients) {
        if (order.getUserId() == null || ingredients.isEmpty()) {
            return;
        }
        List<ClientStock> stockItems = clientStockRepository.findByUserId(order.getUserId());
        if (stockItems == null || stockItems.isEmpty()) {
            return;
        }
        Map<UUID, ClientStock> stockMap = stockItems.stream()
                .filter(cs -> cs.getBaseProduct() != null && cs.getBaseProduct().getId() != null)
                .collect(Collectors.toMap(cs -> cs.getBaseProduct().getId(), Function.identity()));
        for (IngredientAggregateDto item : ingredients) {
            UUID baseProductId = item.baseProductId() != null ? item.baseProductId() : resolveBaseProductId(item.name());
            if (baseProductId == null) {
                continue;
            }
            ClientStock stock = stockMap.get(baseProductId);
            if (stock == null) {
                continue;
            }
            BigDecimal current = orZero(stock.getQty());
            BigDecimal required = orZero(item.totalQty());
            BigDecimal newQty = current.subtract(required);
            stock.setQty(newQty.max(BigDecimal.ZERO));
            clientStockRepository.save(stock);
        }
    }

    private UUID resolveBaseProductId(String name) {
        if (name == null) {
            return null;
        }
        return baseProductRepository.findByNameIgnoreCase(name)
                .map(BaseProduct::getId)
                .orElse(null);
    }

    @Transactional
    public OrderDto cancelOrder(Long id) {
        Order order = getOrderOrThrow(id);
        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(OffsetDateTime.now());
        orderRepo.save(order);
        return orderMapper.toDto(order);
    }

    private void validateOrderPayload(OrderDto dto) {
        if (dto == null || dto.items() == null || dto.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, EMPTY_ORDER_ERROR);
        }
    }

    private Order getOrderOrThrow(Long id) {
        return orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ORDER_NOT_FOUND_MESSAGE));
    }
}
