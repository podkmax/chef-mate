package com.chefmate.api;

import com.chefmate.dto.BaseProductDto;
import com.chefmate.service.BaseProductService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/base-product")
@Tag(name = "BaseProduct")
public class BaseProductController {
    private final BaseProductService baseProductService;

    public BaseProductController(BaseProductService baseProductService) {
        this.baseProductService = baseProductService;
    }

    @GetMapping
    public List<BaseProductDto> all() {
        return baseProductService.findAll();
    }

    @PostMapping
    public ResponseEntity<BaseProductDto> create(@Valid @RequestBody BaseProductDto dto) {
        return ResponseEntity.ok(baseProductService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BaseProductDto> update(@PathVariable UUID id, @Valid @RequestBody BaseProductDto dto) {
        return ResponseEntity.ok(baseProductService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        baseProductService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
