package com.chefmate.api;

import com.chefmate.dto.OrderDto;
import com.chefmate.dto.IngredientAggregateDto;
import com.chefmate.service.OrderExportService;
import com.chefmate.service.OrderService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;
    private final OrderExportService orderExportService;
    public OrderController(OrderService orderService, OrderExportService orderExportService) {
        this.orderService = orderService;
        this.orderExportService = orderExportService;
    }

    @GetMapping("/health")
    public String health() {
        return "orders-api-ok";
    }

    @GetMapping
    public List<OrderDto> all() { return orderService.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDto> one(@PathVariable Long id) {
        var val = orderService.findById(id);
        return val != null ? ResponseEntity.ok(val) : ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<OrderDto> create(@Valid @RequestBody OrderDto dto) {
        return ResponseEntity.ok(orderService.createOrder(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrderDto> update(@PathVariable Long id, @Valid @RequestBody OrderDto dto) {
        return ResponseEntity.ok(orderService.updateOrder(id, dto));
    }

    @PatchMapping("/{id}/confirm")
    public ResponseEntity<OrderDto> confirm(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(orderService.confirmOrder(id));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<OrderDto> cancel(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(orderService.cancelOrder(id));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/aggregate")
    public List<IngredientAggregateDto> aggregate(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean client) {
        return orderService.aggregateIngredients(id, client);
    }

    @GetMapping("/{date}/export")
    public ResponseEntity<byte[]> exportAggregated(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        byte[] data = orderExportService.exportAggregatedIngredients(date);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("ingredients-" + date + ".xlsx").build());
        headers.setContentLength(data.length);
        return ResponseEntity.ok().headers(headers).body(data);
    }
}

