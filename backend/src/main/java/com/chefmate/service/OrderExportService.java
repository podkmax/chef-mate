package com.chefmate.service;

import com.chefmate.dto.IngredientAggregateDto;
import com.chefmate.dto.UnitDto;
import com.chefmate.model.Order;
import com.chefmate.repo.OrderRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderExportService {
    private static final String SHEET_NAME = "Ingredients";
    private static final String HEADER_INGREDIENT = "Ingredient";
    private static final String HEADER_QUANTITY = "Quantity";
    private static final String HEADER_UNIT = "Unit";
    private static final String KEY_DELIMITER = "|";

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    public OrderExportService(OrderRepository orderRepository, OrderService orderService) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
    }

    @Transactional(readOnly = true)
    public byte[] exportAggregatedIngredients(LocalDate date) {
        List<Order> orders = orderRepository.findByTargetDate(date);
        Map<String, IngredientAggregateDto> aggregated = aggregateAcrossOrders(orders);
        return writeWorkbook(aggregated.values());
    }

    private Map<String, IngredientAggregateDto> aggregateAcrossOrders(List<Order> orders) {
        Map<String, IngredientAggregateDto> result = new LinkedHashMap<>();
        for (Order order : orders) {
            if (order.getId() == null) {
                continue;
            }
            List<IngredientAggregateDto> perOrder = orderService.aggregateIngredients(order.getId(), false);
            for (IngredientAggregateDto ingredient : perOrder) {
                String key = buildAggregationKey(ingredient);
                result.merge(key, ingredient, this::mergeIngredients);
            }
        }
        return result;
    }

    private byte[] writeWorkbook(Collection<IngredientAggregateDto> ingredients) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);
            writeHeader(sheet);
            writeRows(sheet, ingredients);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Не удалось сформировать Excel файл.", ex);
        }
    }

    private void writeHeader(Sheet sheet) {
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue(HEADER_INGREDIENT);
        header.createCell(1).setCellValue(HEADER_QUANTITY);
        header.createCell(2).setCellValue(HEADER_UNIT);
    }

    private void writeRows(Sheet sheet, Collection<IngredientAggregateDto> ingredients) {
        int rowIdx = 1;
        for (IngredientAggregateDto item : ingredients) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(valueOrEmpty(item.name()));
            row.createCell(1).setCellValue(formatQuantity(item.totalQty()));
            row.createCell(2).setCellValue(resolveUnitLabel(item));
        }
    }

    private IngredientAggregateDto mergeIngredients(IngredientAggregateDto current, IngredientAggregateDto incoming) {
        BigDecimal total = amountOrZero(current.totalQty()).add(amountOrZero(incoming.totalQty()));
        UnitDto unit = current.unit() != null ? current.unit() : incoming.unit();
        return new IngredientAggregateDto(
                firstNonBlank(current.name(), incoming.name()),
                total,
                current.unitId() != null ? current.unitId() : incoming.unitId(),
                unit,
                firstNonBlank(current.unitShortName(), incoming.unitShortName(), unit != null ? unit.shortName() : null),
                null,
                null,
                current.baseProductId() != null ? current.baseProductId() : incoming.baseProductId());
    }

    private String buildAggregationKey(IngredientAggregateDto ingredient) {
        String namePart = valueOrEmpty(ingredient.name());
        String unitPart = ingredient.unitId() != null
                ? ingredient.unitId().toString()
                : resolveUnitLabel(ingredient);
        return namePart + KEY_DELIMITER + unitPart;
    }

    private String resolveUnitLabel(IngredientAggregateDto ingredient) {
        String explicitShort = firstNonBlank(ingredient.unitShortName());
        if (!explicitShort.isBlank()) {
            return explicitShort;
        }
        UnitDto unit = ingredient.unit();
        return unit != null ? valueOrEmpty(unit.shortName()) : "";
    }

    private String formatQuantity(BigDecimal quantity) {
        return quantity != null ? quantity.stripTrailingZeros().toPlainString() : "";
    }

    private String valueOrEmpty(String value) {
        return value != null ? value : "";
    }

    private String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return "";
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }

    private BigDecimal amountOrZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
