package com.chefmate.service;

import com.chefmate.dto.IngredientAggregateDto;
import com.chefmate.model.Order;
import com.chefmate.repo.OrderRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Ingredients");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Ingredient");
            header.createCell(1).setCellValue("Quantity");
            header.createCell(2).setCellValue("Unit");
            int rowIdx = 1;
            for (IngredientAggregateDto item : aggregated.values()) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(item.name != null ? item.name : "");
                row.createCell(1).setCellValue(
                        item.totalQty != null ? item.totalQty.stripTrailingZeros().toPlainString() : "");
                row.createCell(2).setCellValue(item.unit != null ? item.unit : "");
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Не удалось сформировать Excel файл.", ex);
        }
    }

    private Map<String, IngredientAggregateDto> aggregateAcrossOrders(List<Order> orders) {
        Map<String, IngredientAggregateDto> result = new LinkedHashMap<>();
        for (Order order : orders) {
            if (order.id == null) {
                continue;
            }
            List<IngredientAggregateDto> perOrder = orderService.aggregateIngredients(order.id, false);
            for (IngredientAggregateDto ingredient : perOrder) {
                String name = ingredient.name != null ? ingredient.name : "";
                String unit = ingredient.unit != null ? ingredient.unit : "";
                String key = name + "|" + unit;
                result.compute(key, (k, existing) -> {
                    if (existing == null) {
                        IngredientAggregateDto dto = new IngredientAggregateDto();
                        dto.name = name;
                        dto.unit = unit;
                        dto.totalQty = safeCopy(ingredient.totalQty);
                        return dto;
                    } else {
                        existing.totalQty = safeCopy(existing.totalQty).add(safeCopy(ingredient.totalQty));
                        return existing;
                    }
                });
            }
        }
        return result;
    }

    private BigDecimal safeCopy(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
