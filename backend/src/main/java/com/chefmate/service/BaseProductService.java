package com.chefmate.service;

import com.chefmate.dto.BaseProductDto;
import com.chefmate.model.BaseProduct;
import com.chefmate.repo.BaseProductRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BaseProductService {
    private final BaseProductRepository baseProductRepository;
    private final UnitService unitService;

    public BaseProductService(BaseProductRepository baseProductRepository, UnitService unitService) {
        this.baseProductRepository = baseProductRepository;
        this.unitService = unitService;
    }

    @Transactional(readOnly = true)
    public List<BaseProductDto> findAll() {
        return baseProductRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public BaseProductDto create(BaseProductDto dto) {
        BaseProduct entity = fromDto(dto);
        entity.id = UUID.randomUUID();
        return toDto(baseProductRepository.save(entity));
    }

    @Transactional
    public BaseProductDto update(UUID id, BaseProductDto dto) {
        BaseProduct entity = baseProductRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Base product not found"));
        entity.name = dto.name != null ? dto.name.trim() : entity.name;
        if (dto.unit != null) {
            entity.unit = resolveUnit(dto.unit);
        }
        if (dto.isFreezable != null) {
            entity.isFreezable = dto.isFreezable;
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
        BaseProductDto dto = new BaseProductDto();
        dto.id = entity.id;
        dto.name = entity.name;
        dto.unit = entity.unit;
        dto.isFreezable = entity.isFreezable;
        return dto;
    }

    private BaseProduct fromDto(BaseProductDto dto) {
        BaseProduct entity = new BaseProduct();
        entity.name = dto.name != null ? dto.name.trim() : null;
        entity.unit = dto.unit != null ? resolveUnit(dto.unit) : unitService.getDefaultUnit().shortName;
        entity.isFreezable = dto.isFreezable != null ? dto.isFreezable : Boolean.TRUE;
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
