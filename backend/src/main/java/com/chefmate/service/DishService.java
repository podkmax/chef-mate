package com.chefmate.service;

import com.chefmate.dto.DishDto;
import com.chefmate.dto.DishIngredientDto;
import com.chefmate.model.BaseProduct;
import com.chefmate.model.Dish;
import com.chefmate.model.DishIngredient;
import com.chefmate.model.Unit;
import com.chefmate.repo.BaseProductRepository;
import com.chefmate.repo.DishIngredientRepository;
import com.chefmate.repo.DishRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DishService {
    private final DishRepository dishRepo;
    private final DishIngredientRepository ingredientRepo;
    private final BaseProductRepository baseProductRepo;
    private final UnitService unitService;

    public DishService(
            DishRepository dishRepo,
            DishIngredientRepository ingredientRepo,
            BaseProductRepository baseProductRepo,
            UnitService unitService) {
        this.dishRepo = dishRepo;
        this.ingredientRepo = ingredientRepo;
        this.baseProductRepo = baseProductRepo;
        this.unitService = unitService;
    }

    @Transactional(readOnly = true)
    public List<DishDto> getActiveDishes() {
        return dishRepo.findByActiveTrue().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DishDto getDish(Long id) {
        return dishRepo.findById(id).map(this::toDto).orElse(null);
    }

    @Transactional
    public DishDto createDish(DishDto dto) {
        Dish dish = fromDto(dto);
        dish.active = true;
        dishRepo.save(dish);
        return toDto(dish);
    }

    @Transactional
    public DishDto updateDish(Long id, DishDto dto) {
        Dish d = dishRepo.findById(id).orElseThrow();
        d.category = dto.category;
        d.title = dto.title;
        d.description = dto.description;
        List<DishIngredient> targetIngredients = d.ingredients != null ? d.ingredients : new ArrayList<>();
        targetIngredients.clear();
        if (dto.ingredients != null) {
            for (DishIngredientDto ingrDto : dto.ingredients) {
                DishIngredient ingr = fromIngredientDto(ingrDto);
                ingr.dish = d;
                targetIngredients.add(ingr);
            }
        }
        d.ingredients = targetIngredients;
        dishRepo.save(d);
        return toDto(d);
    }

    @Transactional
    public void softDelete(Long id) {
        Dish entity = dishRepo.findById(id).orElseThrow();
        entity.active = false;
        dishRepo.save(entity);
    }

    private DishDto toDto(Dish d) {
        DishDto r = new DishDto();
        r.id = d.id;
        r.category = d.category;
        r.title = d.title;
        r.description = d.description;
        r.active = d.active;
        if (d.ingredients != null)
            r.ingredients = d.ingredients.stream().map(this::toIngredientDto).collect(Collectors.toList());
        return r;
    }
    private DishIngredientDto toIngredientDto(DishIngredient e) {
        DishIngredientDto r = new DishIngredientDto();
        r.id = e.id;
        r.name = e.name;
        r.qty = e.qty;
        if (e.unit != null) {
            r.unitId = e.unit.id;
            r.unit = unitService.toDto(e.unit);
        }
        r.excludeForClient = e.excludeForClient;
        r.baseProductId = e.baseProduct != null ? e.baseProduct.id : null;
        return r;
    }
    private Dish fromDto(DishDto d) {
        Dish entity = new Dish();
        entity.category = d.category;
        entity.title = d.title;
        entity.description = d.description;
        entity.active = d.active != null ? d.active : true;
        List<DishIngredient> ingredients = new ArrayList<>();
        if (d.ingredients != null) {
            ingredients = d.ingredients.stream().map(this::fromIngredientDto).collect(Collectors.toList());
        }
        for (DishIngredient ingr : ingredients) {
            ingr.dish = entity;
        }
        entity.ingredients = ingredients;
        return entity;
    }
    private DishIngredient fromIngredientDto(DishIngredientDto d) {
        DishIngredient e = new DishIngredient();
        e.name = d.name;
        e.qty = d.qty;
        e.excludeForClient = d.excludeForClient != null ? d.excludeForClient : false;
        Unit unit = resolveUnit(d);
        e.unit = unit;
        BaseProduct baseProduct = resolveBaseProduct(d, unit);
        e.baseProduct = baseProduct;
        return e;
    }

    private BaseProduct resolveBaseProduct(DishIngredientDto dto, Unit unit) {
        BaseProduct baseProduct = null;
        if (dto.baseProductId != null) {
            baseProduct = baseProductRepo.findById(dto.baseProductId).orElse(null);
        }
        if (baseProduct != null) {
            return baseProduct;
        }
        String name = dto.name != null ? dto.name.trim() : null;
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Ингредиент должен иметь название.");
        }
        baseProduct = baseProductRepo.findByNameIgnoreCase(name).orElse(null);
        if (baseProduct != null) {
            return baseProduct;
        }
        BaseProduct created = new BaseProduct();
        created.name = name;
        created.unit = unit != null ? unit.shortName : unitService.getDefaultUnit().shortName;
        created.isFreezable = Boolean.TRUE;
        return baseProductRepo.save(created);
    }

    private Unit resolveUnit(DishIngredientDto dto) {
        if (dto.unitId == null) {
            throw new IllegalArgumentException("Для ингредиента необходимо указать единицу измерения.");
        }
        return unitService.getRequiredUnit(dto.unitId);
    }

}
