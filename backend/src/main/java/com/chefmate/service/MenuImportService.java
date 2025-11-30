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
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MenuImportService {
    private final DishRepository dishRepository;
    private final BaseProductRepository baseProductRepository;
    private final UnitService unitService;
    private final DataFormatter formatter = new DataFormatter();

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
            ParsedImportResult result = parseSheet(sheet);
            ImportStats stats = persistDishes(result.dishes(), result.skippedRows());
            return new MenuImportSummary(stats.created(), stats.updated(), stats.skipped());
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось прочитать Excel файл.", ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private ParsedImportResult parseSheet(Sheet sheet) {
        Map<String, ImportedDish> dishes = new LinkedHashMap<>();
        int skippedRows = 0;
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            ParsedRow parsedRow = parseRow(row);
            if (parsedRow == null) {
                skippedRows++;
                continue;
            }
            ImportedDish importedDish = dishes.computeIfAbsent(parsedRow.dishName(), ImportedDish::new);
            if (parsedRow.description() != null && !parsedRow.description().isBlank()) {
                importedDish.setDescription(parsedRow.description().trim());
            }
            ImportedIngredient ingredient = parsedRow.ingredient();
            if (ingredient == null) {
                skippedRows++;
                continue;
            }
            importedDish.addIngredient(ingredient);
        }
        return new ParsedImportResult(dishes, skippedRows);
    }

    private ParsedRow parseRow(Row row) {
        String dishName = getString(row.getCell(0));
        if (dishName == null || dishName.isBlank()) {
            return null;
        }
        String description = getString(row.getCell(1));
        String ingredientName = getString(row.getCell(2));
        if (ingredientName == null || ingredientName.isBlank()) {
            return null;
        }
        BigDecimal qty;
        try {
            qty = parseDecimal(row.getCell(3));
        } catch (NumberFormatException ex) {
            return null;
        }
        if (qty == null) {
            qty = BigDecimal.ZERO;
        }
        Unit unit = unitService.resolveUnitOrDefault(getString(row.getCell(4)));
        Boolean exclude = parseBoolean(row.getCell(5));
        BaseProduct baseProduct = resolveBaseProduct(ingredientName.trim(), unit);
        ImportedIngredient ingredient = new ImportedIngredient(
                ingredientName.trim(),
                qty,
                unit,
                Boolean.TRUE.equals(exclude),
                baseProduct
        );
        return new ParsedRow(dishName.trim(), description, ingredient);
    }

    private ImportStats persistDishes(Map<String, ImportedDish> dishes, int initialSkipped) {
        int skippedRows = initialSkipped;
        int created = 0;
        int updated = 0;
        OffsetDateTime now = OffsetDateTime.now();
        for (ImportedDish imported : dishes.values()) {
            if (imported.getIngredients().isEmpty()) {
                skippedRows++;
                continue;
            }
            Optional<Dish> existing = dishRepository.findByTitleIgnoreCase(imported.getName());
            if (existing.isPresent()) {
                applyDishUpdate(imported, existing.get(), now);
                updated++;
            } else {
                createDish(imported, now);
                created++;
            }
        }
        return new ImportStats(created, updated, skippedRows);
    }

    private void applyDishUpdate(ImportedDish imported, Dish existing, OffsetDateTime now) {
        if (imported.getDescription() != null) {
            existing.setDescription(imported.getDescription());
        }
        existing.setUpdatedAt(now);
        replaceIngredients(existing, imported.getIngredients());
        dishRepository.save(existing);
    }

    private void createDish(ImportedDish imported, OffsetDateTime now) {
        Dish fresh = new Dish();
        fresh.setTitle(imported.getName());
        fresh.setDescription(imported.getDescription());
        fresh.setCategory("Imported");
        fresh.setActive(true);
        fresh.setCreatedAt(now);
        fresh.setUpdatedAt(now);
        fresh.setIngredients(new ArrayList<>());
        for (ImportedIngredient ingredient : imported.getIngredients()) {
            DishIngredient di = new DishIngredient();
            di.setDish(fresh);
            di.setName(ingredient.name());
            di.setQty(ingredient.qty());
            di.setUnit(ingredient.unit());
            di.setBaseProduct(ingredient.baseProduct());
            di.setExcludeForClient(ingredient.excludeForClient());
            fresh.getIngredients().add(di);
        }
        dishRepository.save(fresh);
    }


    private void replaceIngredients(Dish dish, List<ImportedIngredient> ingredients) {
        if (dish.getIngredients() == null) {
            dish.setIngredients(new ArrayList<>());
        } else {
            dish.getIngredients().clear();
        }
        for (ImportedIngredient imported : ingredients) {
            DishIngredient di = new DishIngredient();
            di.setDish(dish);
            di.setName(imported.name());
            di.setQty(imported.qty());
            di.setUnit(imported.unit());
            di.setBaseProduct(imported.baseProduct());
            di.setExcludeForClient(imported.excludeForClient());
            dish.getIngredients().add(di);
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
            bp.setName(name);
            String unitShort = unit != null ? unit.getShortName() : unitService.getDefaultUnit().getShortName();
            bp.setUnit(unitShort);
            bp.setIsFreezable(Boolean.TRUE);
            return baseProductRepository.save(bp);
        });
    }

    private record ParsedImportResult(Map<String, ImportedDish> dishes, int skippedRows) { }

    private record ParsedRow(String dishName, String description, ImportedIngredient ingredient) { }

    private record ImportStats(int created, int updated, int skipped) { }
}
