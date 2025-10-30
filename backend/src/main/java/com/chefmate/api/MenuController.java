package com.chefmate.api;

import com.chefmate.dto.DishDto;
import com.chefmate.dto.DishIngredientDto;
import com.chefmate.service.DishService;
import com.chefmate.service.MenuImportService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/menu")
@Validated
public class MenuController {
    private final DishService dishService;
    private final MenuImportService menuImportService;
    public MenuController(DishService dishService, MenuImportService menuImportService) {
        this.dishService = dishService;
        this.menuImportService = menuImportService;
    }

    @GetMapping("/health")
    public String health() {
        return "menu-api-ok";
    }

    @GetMapping
    public List<DishDto> getMenu() {
        return dishService.getActiveDishes();
    }

    @GetMapping("/{id}")
    public ResponseEntity<DishDto> getDish(@PathVariable Long id) {
        DishDto dish = dishService.getDish(id);
        return dish != null ? ResponseEntity.ok(dish) : ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<DishDto> create(@RequestBody @Valid DishDto dishDto) {
        return ResponseEntity.ok(dishService.createDish(dishDto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DishDto> update(@PathVariable Long id, @RequestBody @Valid DishDto dishDto) {
        return ResponseEntity.ok(dishService.updateDish(id, dishDto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> softDelete(@PathVariable Long id) {
        dishService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/import")
    public ResponseEntity<MenuImportService.MenuImportSummary> importMenu(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(menuImportService.importMenu(file));
    }
}

