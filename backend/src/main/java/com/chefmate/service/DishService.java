package com.chefmate.service;

import com.chefmate.dto.DishDto;
import com.chefmate.dto.DishIngredientDto;
import com.chefmate.dto.UnitDto;
import com.chefmate.model.BaseProduct;
import com.chefmate.model.Dish;
import com.chefmate.model.DishIngredient;
import com.chefmate.model.Unit;
import com.chefmate.repo.BaseProductRepository;
import com.chefmate.repo.DishRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DishService {
    private final DishRepository dishRepo;
    private final BaseProductRepository baseProductRepo;
    private final UnitService unitService;

    public DishService(
            DishRepository dishRepo,
            BaseProductRepository baseProductRepo,
            UnitService unitService) {
        this.dishRepo = dishRepo;
        this.baseProductRepo = baseProductRepo;
        this.unitService = unitService;
    }

    @Transactional(readOnly = true)
    public List<DishDto> getActiveDishes() {
        return dishRepo.findByActiveTrue().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public DishDto getDish(Long id) {
        return dishRepo.findById(id).map(this::toDto).orElse(null);
    }

    @Transactional
    public DishDto createDish(DishDto dto) {
        Dish dish = fromDto(dto);
        dish.setActive(true);
        dishRepo.save(dish);
        return toDto(dish);
    }

    @Transactional
    public DishDto updateDish(Long id, DishDto dto) {
        Dish d = dishRepo.findById(id).orElseThrow();
        d.setCategory(dto.category());
        d.setTitle(dto.title());
        d.setDescription(dto.description());
        List<DishIngredient> targetIngredients = new ArrayList<>();
        if (dto.ingredients() != null) {
            for (DishIngredientDto ingrDto : dto.ingredients()) {
                DishIngredient ingr = fromIngredientDto(ingrDto);
                ingr.setDish(d);
                targetIngredients.add(ingr);
            }
        }
        if (d.getIngredients() == null) {
            d.setIngredients(new ArrayList<>());
        } else {
            d.getIngredients().clear();
        }
        d.getIngredients().addAll(targetIngredients);
        dishRepo.save(d);
        return toDto(d);
    }

    @Transactional
    public void softDelete(Long id) {
        Dish entity = dishRepo.findById(id).orElseThrow();
        entity.setActive(false);
        dishRepo.save(entity);
    }

    private DishDto toDto(Dish d) {
        List<DishIngredientDto> ingredients = d.getIngredients() != null
                ? d.getIngredients().stream().map(this::toIngredientDto).collect(Collectors.toList())
                : List.of();
        return new DishDto(d.getId(), d.getCategory(), d.getTitle(), d.getDescription(), d.getActive(), ingredients);
    }
    private DishIngredientDto toIngredientDto(DishIngredient ingredient) {
        UnitDto unitDto = ingredient.getUnit() != null ? unitService.toDto(ingredient.getUnit()) : null;
        UUID unitId = ingredient.getUnit() != null ? ingredient.getUnit().getId() : null;
        UUID baseProductId = ingredient.getBaseProduct() != null ? ingredient.getBaseProduct().getId() : null;
        return new DishIngredientDto(
                ingredient.getId(),
                ingredient.getName(),
                ingredient.getQty(),
                unitId,
                unitDto,
                ingredient.getExcludeForClient(),
                baseProductId);
    }
    private Dish fromDto(DishDto d) {
        Dish entity = new Dish();
        entity.setCategory(d.category());
        entity.setTitle(d.title());
        entity.setDescription(d.description());
        entity.setActive(d.active() != null ? d.active() : true);
        List<DishIngredient> ingredients = d.ingredients() != null
                ? d.ingredients().stream().map(this::fromIngredientDto).collect(Collectors.toList())
                : new ArrayList<>();
        for (DishIngredient ingredient : ingredients) {
            ingredient.setDish(entity);
        }
        entity.setIngredients(ingredients);
        return entity;
    }
    private DishIngredient fromIngredientDto(DishIngredientDto d) {
        DishIngredient e = new DishIngredient();
        e.setName(d.name());
        e.setQty(d.qty());
        e.setExcludeForClient(d.excludeForClient() != null ? d.excludeForClient() : false);
        Unit unit = resolveUnit(d);
        e.setUnit(unit);
        BaseProduct baseProduct = resolveBaseProduct(d, unit);
        e.setBaseProduct(baseProduct);
        return e;
    }

    private BaseProduct resolveBaseProduct(DishIngredientDto dto, Unit unit) {
        BaseProduct baseProduct = null;
        if (dto.baseProductId() != null) {
            baseProduct = baseProductRepo.findById(dto.baseProductId()).orElse(null);
        }
        if (baseProduct != null) {
            return baseProduct;
        }
        String name = dto.name() != null ? dto.name().trim() : null;
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Ингредиент должен иметь название.");
        }
        baseProduct = baseProductRepo.findByNameIgnoreCase(name).orElse(null);
        if (baseProduct != null) {
            return baseProduct;
        }
        BaseProduct created = new BaseProduct();
        created.setName(name);
        String unitShort = unit != null ? unit.getShortName() : unitService.getDefaultUnit().getShortName();
        created.setUnit(unitShort);
        created.setIsFreezable(Boolean.TRUE);
        return baseProductRepo.save(created);
    }

    private Unit resolveUnit(DishIngredientDto dto) {
        if (dto.unitId() == null) {
            throw new IllegalArgumentException("Для ингредиента необходимо указать единицу измерения.");
        }
        return unitService.getRequiredUnit(dto.unitId());
    }

}
