package com.chefmate.service;

import com.chefmate.dto.BaseProductDto;
import com.chefmate.model.BaseProduct;
import com.chefmate.repo.BaseProductRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class BaseProductService {
    private final BaseProductRepository baseProductRepository;
    private final UnitService unitService;

    @Transactional(readOnly = true)
    public List<BaseProductDto> findAll() {
        return baseProductRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional
    public BaseProductDto create(BaseProductDto dto) {
        BaseProduct entity = fromDto(dto);
        entity.setId(UUID.randomUUID());
        return toDto(baseProductRepository.save(entity));
    }

    @Transactional
    public BaseProductDto update(UUID id, BaseProductDto dto) {
        BaseProduct entity = baseProductRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Base product not found"));
        if (dto.name() != null) {
            entity.setName(dto.name().trim());
        }
        if (dto.unit() != null) {
            entity.setUnit(resolveUnit(dto.unit()));
        }
        if (dto.isFreezable() != null) {
            entity.setIsFreezable(dto.isFreezable());
        }
        return toDto(baseProductRepository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        if (!baseProductRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Base product not found");
        }
        baseProductRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public BaseProductDto findById(UUID id) {
        return baseProductRepository.findById(id).map(this::toDto).orElse(null);
    }

    private BaseProductDto toDto(BaseProduct entity) {
        return new BaseProductDto(entity.getId(), entity.getName(), entity.getUnit(), entity.getIsFreezable());
    }

    private BaseProduct fromDto(BaseProductDto dto) {
        BaseProduct entity = new BaseProduct();
        entity.setName(dto.name() != null ? dto.name().trim() : null);
        String unitShort = dto.unit() != null ? resolveUnit(dto.unit()) : unitService.getDefaultUnit().getShortName();
        entity.setUnit(unitShort);
        entity.setIsFreezable(dto.isFreezable() != null ? dto.isFreezable() : Boolean.TRUE);
        return entity;
    }

    private String resolveUnit(String raw) {
        String shortName = unitService.normalizeShortName(raw);
        if (!unitService.isKnownShortName(shortName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестная единица измерения: " + raw);
        }
        return shortName;
    }
}
