package com.chefmate.service;

import com.chefmate.dto.UnitDto;
import com.chefmate.model.Unit;
import com.chefmate.repo.UnitRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UnitService {
    private static final String DEFAULT_SHORT_NAME = "г";
    private static final Map<String, String> ALIAS_MAP = Map.ofEntries(
            Map.entry("g", "г"),
            Map.entry("gr", "г"),
            Map.entry("gram", "г"),
            Map.entry("grams", "г"),
            Map.entry("гр", "г"),
            Map.entry("г.", "г"),
            Map.entry("гр.", "г"),
            Map.entry("г", "г"),
            Map.entry("кг", "кг"),
            Map.entry("кг.", "кг"),
            Map.entry("kg", "кг"),
            Map.entry("килограмм", "кг"),
            Map.entry("килограммы", "кг"),
            Map.entry("ml", "мл"),
            Map.entry("мл.", "мл"),
            Map.entry("мл", "мл"),
            Map.entry("миллилитр", "мл"),
            Map.entry("миллилитры", "мл"),
            Map.entry("l", "л"),
            Map.entry("л.", "л"),
            Map.entry("литр", "л"),
            Map.entry("литры", "л"),
            Map.entry("л", "л"),
            Map.entry("шт", "шт"),
            Map.entry("шт.", "шт"),
            Map.entry("штука", "шт"),
            Map.entry("штуки", "шт"),
            Map.entry("piece", "шт"),
            Map.entry("pieces", "шт"),
            Map.entry("pcs", "шт"),
            Map.entry("уп", "уп"),
            Map.entry("уп.", "уп"),
            Map.entry("упаковка", "уп"),
            Map.entry("упаковки", "уп"),
            Map.entry("зуб", "зуб"),
            Map.entry("зубчик", "зуб"),
            Map.entry("зубчики", "зуб"),
            Map.entry("зубчиков", "зуб"),
            Map.entry("гол", "гол"),
            Map.entry("головка", "гол"),
            Map.entry("головки", "гол")
    );

    private final UnitRepository unitRepository;
    private final Map<String, Unit> cacheByShortName = new ConcurrentHashMap<>();

    public UnitService(UnitRepository unitRepository) {
        this.unitRepository = unitRepository;
    }

    @Transactional(readOnly = true)
    public List<UnitDto> getAllUnits() {
        return unitRepository.findAll().stream()
                .map(this::toDto)
                .sorted(Comparator.comparing(
                        dto -> dto.name() != null ? dto.name() : dto.shortName(),
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Unit getRequiredUnit(UUID id) {
        return unitRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Единица измерения не найдена."));
    }

    @Transactional(readOnly = true)
    public Unit resolveUnitOrDefault(String raw) {
        String shortName = normalizeShortName(raw);
        Unit unit = findByShortName(shortName);
        if (unit != null) {
            return unit;
        }
        Unit fallback = findByShortName(DEFAULT_SHORT_NAME);
        if (fallback == null) {
            throw new IllegalStateException("Справочник единиц измерения не инициализирован.");
        }
        return fallback;
    }

    @Transactional(readOnly = true)
    public Unit getDefaultUnit() {
        Unit unit = findByShortName(DEFAULT_SHORT_NAME);
        if (unit == null) {
            throw new IllegalStateException("Справочник единиц измерения не инициализирован.");
        }
        return unit;
    }

    @Transactional(readOnly = true)
    public String normalizeShortName(String raw) {
        if (raw == null) {
            return DEFAULT_SHORT_NAME;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return DEFAULT_SHORT_NAME;
        }
        return ALIAS_MAP.getOrDefault(normalized, normalized);
    }

    @Transactional(readOnly = true)
    public boolean isKnownShortName(String shortName) {
        if (shortName == null) {
            return false;
        }
        return findByShortName(shortName) != null;
    }

    public UnitDto toDto(Unit unit) {
        if (unit == null) {
            return null;
        }
        return new UnitDto(unit.getId(), unit.getName(), unit.getShortName());
    }

    private Unit findByShortName(String shortName) {
        if (shortName == null) {
            return null;
        }
        return cacheByShortName.computeIfAbsent(shortName, key ->
                unitRepository.findByShortNameIgnoreCase(key).orElse(null));
    }
}
