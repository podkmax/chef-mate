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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        order.createdAt = now;
        order.updatedAt = now;
        order.status = OrderStatus.CREATED;
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
        Order entity = orderRepo.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Заказ не найден"));
        orderMapper.updateEntityFromDto(dto, entity);
        entity.updatedAt = OffsetDateTime.now();
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
        List<CookOrderDishDto> result = new java.util.ArrayList<>();
        for (OrderItem item : order.items) {
            CookOrderDishDto summary = new CookOrderDishDto();
            summary.dishId = item.dishId;
            Dish dish = dishMap.get(item.dishId);
            summary.name = dish != null ? dish.title : ("Блюдо #" + item.dishId);
            summary.portions = item.portions != null ? item.portions : 1;
            result.add(summary);
        }
        return result;
    }

    private List<IngredientAggregateDto> aggregateIngredients(Order order, Map<Long, Dish> dishMap, boolean forClient) {
        if (order == null || order.items == null || order.items.isEmpty()) {
            return List.of();
        }
        Map<Long, Dish> effectiveDishMap = dishMap != null ? dishMap : loadDishMap(order);
        List<IngredientAggregateDto> aggregates = forClient
                ? aggregateIngredientsForClient(order, effectiveDishMap)
                : aggregateIngredientsForCook(order, effectiveDishMap);
        if (forClient) {
            applyStockAdjustments(order, aggregates, true);
        }
        return aggregates;
    }

    private List<IngredientAggregateDto> aggregateIngredientsForClient(Order order, Map<Long, Dish> dishMap) {
        Map<String, IngredientAggregateDto> aggr = new LinkedHashMap<>();
        for (OrderItem item : order.items) {
            Dish dish = dishMap.get(item.dishId);
            if (dish == null || dish.ingredients == null) {
                continue;
            }
            for (DishIngredient ingredient : dish.ingredients) {
                if (Boolean.TRUE.equals(ingredient.excludeForClient)) {
                    continue;
                }
                BaseProduct baseProduct = ingredient.baseProduct != null
                        ? ingredient.baseProduct
                        : baseProductRepository.findByNameIgnoreCase(ingredient.name).orElse(null);
                if (baseProduct == null) {
                    continue;
                }
                Unit unit = ingredient.unit != null
                        ? ingredient.unit
                        : unitService.resolveUnitOrDefault(baseProduct.unit);
                String unitShort = resolveUnitShortName(unit, baseProduct);
                BigDecimal quantity = calculateQuantity(ingredient, item);
                String displayName = baseProduct.name != null ? baseProduct.name : ingredient.name;
                String key = normalizeKey(displayName) + "|" + (unit != null ? unit.id : "default");
                aggr.compute(key, (mapKey, current) -> {
                    if (current == null) {
                        IngredientAggregateDto dto = new IngredientAggregateDto();
                        dto.name = displayName;
                        dto.totalQty = quantity;
                        dto.requiredQty = quantity;
                        dto.unitId = unit != null ? unit.id : null;
                        dto.unit = unit != null ? unitService.toDto(unit) : null;
                        dto.unitShortName = unitShort;
                        dto.baseProductId = baseProduct.id;
                        return dto;
                    }
                    current.totalQty = current.totalQty.add(quantity);
                    current.requiredQty = current.totalQty;
                    if (current.unitShortName == null || current.unitShortName.isBlank()) {
                        current.unitShortName = unitShort;
                    }
                    return current;
                });
            }
        }
        return new java.util.ArrayList<>(aggr.values());
    }

    private List<IngredientAggregateDto> aggregateIngredientsForCook(Order order, Map<Long, Dish> dishMap) {
        Map<String, IngredientAggregateDto> aggr = new LinkedHashMap<>();
        for (OrderItem item : order.items) {
            Dish dish = dishMap.get(item.dishId);
            if (dish == null || dish.ingredients == null) {
                continue;
            }
            for (DishIngredient ingredient : dish.ingredients) {
                Unit unit = ingredient.unit != null ? ingredient.unit : unitService.getDefaultUnit();
                String unitShort = resolveUnitShortName(unit, ingredient.baseProduct);
                BigDecimal quantity = calculateQuantity(ingredient, item);
                String name = ingredient.name != null && !ingredient.name.isBlank()
                        ? ingredient.name
                        : (ingredient.baseProduct != null ? ingredient.baseProduct.name : "Ингредиент");
                String key = normalizeKey(name) + "|" + (unit != null ? unit.id : "default");
                aggr.compute(key, (mapKey, current) -> {
                    if (current == null) {
                        IngredientAggregateDto dto = new IngredientAggregateDto();
                        dto.name = name;
                        dto.totalQty = quantity;
                        dto.unitId = unit != null ? unit.id : null;
                        dto.unit = unit != null ? unitService.toDto(unit) : null;
                        dto.unitShortName = unitShort;
                        dto.baseProductId = ingredient.baseProduct != null ? ingredient.baseProduct.id : null;
                        return dto;
                    }
                    current.totalQty = current.totalQty.add(quantity);
                    if (current.unitShortName == null || current.unitShortName.isBlank()) {
                        current.unitShortName = unitShort;
                    }
                    return current;
                });
            }
        }
        return new java.util.ArrayList<>(aggr.values());
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
            return orderMapper.toDto(order);
        }
        order.status = OrderStatus.CONFIRMED;
        order.updatedAt = OffsetDateTime.now();
        orderRepo.save(order);
        Map<Long, Dish> dishMap = loadDishMap(order);
        List<IngredientAggregateDto> ingredients = aggregateIngredients(order, dishMap, true);
        adjustClientStock(order, ingredients);
        clientNotificationService.notifyOrderConfirmed(order, ingredients);
        return orderMapper.toDto(order);
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
        return orderMapper.toDto(order);
    }

    private void validateOrderPayload(OrderDto dto) {
        if (dto == null || dto.items == null || dto.items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Заказ должен содержать хотя бы одну позицию.");
        }
    }
}
