package com.chefmate.api;

import com.chefmate.dto.ClientStockDto;
import com.chefmate.service.ClientStockService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/client/{userId}/stock")
@Tag(name = "ClientStock")
public class ClientStockController {
    private final ClientStockService clientStockService;

    public ClientStockController(ClientStockService clientStockService) {
        this.clientStockService = clientStockService;
    }

    @GetMapping
    public List<ClientStockDto> list(@PathVariable Long userId) {
        return clientStockService.getClientStock(userId);
    }

    @PostMapping
    public ResponseEntity<ClientStockDto> save(
            @PathVariable Long userId,
            @Valid @RequestBody ClientStockDto dto) {
        return ResponseEntity.ok(clientStockService.saveStock(userId, dto));
    }

    @DeleteMapping("/{stockId}")
    public ResponseEntity<Void> delete(@PathVariable Long userId, @PathVariable UUID stockId) {
        clientStockService.deleteStock(userId, stockId);
        return ResponseEntity.noContent().build();
    }
}
