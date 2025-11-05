package com.chefmate.service;

import com.chefmate.model.BaseProduct;
import com.chefmate.model.Dish;
import com.chefmate.model.DishIngredient;
import com.chefmate.model.Unit;
import com.chefmate.repo.BaseProductRepository;
import com.chefmate.repo.DishRepository;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MenuImportService {
    private final DishRepository dishRepository;
    private final BaseProductRepository baseProductRepository;
    private final UnitService unitService;
    private final DataFormatter formatter = new DataFormatter();

    public MenuImportService(
            DishRepository dishRepository,
            BaseProductRepository baseProductRepository,
            UnitService unitService) {
        this.dishRepository = dishRepository;
        this.baseProductRepository = baseProductRepository;
        this.unitService = unitService;
    }

    public record MenuImportSummary(int created, int updated, int skipped) { }

    @Transactional
    public MenuImportSummary importMenu(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Файл не найден или пуст.");
        }
        try (InputStream in = file.getInputStream(); Workbook workbook = new XSSFWorkbook(in)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "В файле отсутствуют листы.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, ImportedDish> dishes = new LinkedHashMap<>();
            int skippedRows = 0;
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                String dishName = getString(row.getCell(0));
                if (dishName == null || dishName.isBlank()) {
                    skippedRows++;
                    continue;
                }
                ImportedDish importedDish = dishes.computeIfAbsent(dishName.trim(), ImportedDish::new);
                String description = getString(row.getCell(1));
                if (description != null && !description.isBlank()) {
                    importedDish.description = description.trim();
                }
                String ingredientName = getString(row.getCell(2));
                if (ingredientName == null || ingredientName.isBlank()) {
                    skippedRows++;
                    continue;
                }
                BigDecimal qty;
                try {
                    qty = parseDecimal(row.getCell(3));
                } catch (NumberFormatException ex) {
                    skippedRows++;
                    continue;
                }
                if (qty == null) {
                    qty = BigDecimal.ZERO;
                }
                Unit unit = unitService.resolveUnitOrDefault(getString(row.getCell(4)));
                Boolean exclude = parseBoolean(row.getCell(5));
                BaseProduct baseProduct = resolveBaseProduct(ingredientName.trim(), unit);
                importedDish.ingredients.add(new ImportedIngredient(
                        ingredientName.trim(),
                        qty,
                        unit,
                        Boolean.TRUE.equals(exclude),
                        baseProduct
                ));
            }
            int created = 0;
            int updated = 0;
            OffsetDateTime now = OffsetDateTime.now();
            for (ImportedDish imported : dishes.values()) {
                if (imported.ingredients.isEmpty()) {
                    skippedRows++;
                    continue;
                }
                Optional<Dish> existingOpt = dishRepository.findByTitleIgnoreCase(imported.name);
                if (existingOpt.isPresent()) {
                    Dish existing = existingOpt.get();
                    if (imported.description != null) {
                        existing.description = imported.description;
                    }
                    existing.updatedAt = now;
                    replaceIngredients(existing, imported.ingredients);
                    dishRepository.save(existing);
                    updated++;
                } else {
                    Dish fresh = new Dish();
                    fresh.title = imported.name;
                    fresh.description = imported.description;
                    fresh.category = "Imported";
                    fresh.active = true;
                    fresh.createdAt = now;
                    fresh.updatedAt = now;
                    fresh.ingredients = new ArrayList<>();
                    for (ImportedIngredient ingr : imported.ingredients) {
                        DishIngredient di = new DishIngredient();
                        di.dish = fresh;
                        di.name = ingr.name;
                        di.qty = ingr.qty;
                        di.unit = ingr.unit;
                        di.baseProduct = ingr.baseProduct;
                        di.excludeForClient = ingr.excludeForClient;
                        fresh.ingredients.add(di);
                    }
                    dishRepository.save(fresh);
                    created++;
                }
            }
            return new MenuImportSummary(created, updated, skippedRows);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось прочитать Excel файл.", ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private void replaceIngredients(Dish dish, List<ImportedIngredient> ingredients) {
        if (dish.ingredients == null) {
            dish.ingredients = new ArrayList<>();
        } else {
            dish.ingredients.clear();
        }
        for (ImportedIngredient imported : ingredients) {
            DishIngredient di = new DishIngredient();
            di.dish = dish;
            di.name = imported.name;
            di.qty = imported.qty;
            di.unit = imported.unit;
            di.baseProduct = imported.baseProduct;
            di.excludeForClient = imported.excludeForClient;
            dish.ingredients.add(di);
        }
    }

    private String getString(Cell cell) {
        if (cell == null) {
            return null;
        }
        String value = formatter.formatCellValue(cell);
        return value != null && !value.isBlank() ? value.trim() : null;
    }

    private BigDecimal parseDecimal(Cell cell) {
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
            case STRING -> {
                String raw = cell.getStringCellValue();
                if (raw == null || raw.isBlank()) {
                    yield null;
                }
                try {
                    yield new BigDecimal(raw.trim().replace(",", "."));
                } catch (NumberFormatException ex) {
                    throw ex;
                }
            }
            case FORMULA -> {
                String formatted = formatter.formatCellValue(cell);
                if (formatted == null || formatted.isBlank()) {
                    yield null;
                }
                yield new BigDecimal(formatted.trim().replace(",", "."));
            }
            default -> null;
        };
    }

    private Boolean parseBoolean(Cell cell) {
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case BOOLEAN -> cell.getBooleanCellValue();
            case STRING -> {
                String raw = cell.getStringCellValue();
                if (raw == null) {
                    yield null;
                }
                String normalized = raw.trim().toLowerCase(Locale.ROOT);
                yield switch (normalized) {
                    case "1", "true", "yes", "y", "да" -> Boolean.TRUE;
                    case "0", "false", "no", "n", "нет" -> Boolean.FALSE;
                    default -> null;
                };
            }
            case NUMERIC -> cell.getNumericCellValue() != 0;
            default -> null;
        };
    }

    private BaseProduct resolveBaseProduct(String name, Unit unit) {
        return baseProductRepository.findByNameIgnoreCase(name).orElseGet(() -> {
            BaseProduct bp = new BaseProduct();
            bp.name = name;
            bp.unit = unit != null ? unit.shortName : unitService.getDefaultUnit().shortName;
            bp.isFreezable = Boolean.TRUE;
            return baseProductRepository.save(bp);
        });
    }

    private static final class ImportedDish {
        final String name;
        String description;
        final List<ImportedIngredient> ingredients = new ArrayList<>();

        ImportedDish(String name) {
            this.name = name;
        }
    }

    private record ImportedIngredient(String name, BigDecimal qty, Unit unit, boolean excludeForClient, BaseProduct baseProduct) { }
}
