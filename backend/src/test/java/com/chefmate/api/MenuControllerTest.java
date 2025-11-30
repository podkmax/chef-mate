package com.chefmate.api;

import com.chefmate.AbstractApplicationTest;
import com.chefmate.dto.DishDto;
import com.chefmate.dto.DishIngredientDto;
import com.chefmate.dto.UnitDto;
import com.chefmate.model.Unit;
import com.chefmate.repo.UnitRepository;
import com.chefmate.service.DishService;
import com.chefmate.service.MenuImportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MenuControllerTest extends AbstractApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private DishService dishService;
    @BeforeEach
    void setUp() {
        ensureUnitExists();
    }

    @Test
    void healthReturnsOk() throws Exception {
        mockMvc.perform(get("/api/menu/health"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string("menu-api-ok"));
    }

    @Test
    @Disabled
    void getMenuReturnsDishes() throws Exception {
        DishDto dish = persistDish("Soup");

        String responseString = mockMvc.perform(get("/api/menu"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        DishDto[] dishDtos = objectMapper.readValue(responseString, DishDto[].class);
        List<DishDto> dishes = Arrays.asList(dishDtos);
        Assertions.assertEquals(dishes.getFirst().title(), dish.title());
        Assertions.assertEquals(dishes.getFirst().ingredients().getFirst().unit().shortName(), dish.ingredients().getFirst().unit().shortName());
    }

    @Test
    void getMenuReturnsIngredientUnits() throws Exception {
        DishDto dish = persistDish("Salad");

        String responseString = mockMvc.perform(get("/api/menu"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        DishDto[] dishDtos = objectMapper.readValue(responseString, DishDto[].class);
        List<DishDto> dishes = Arrays.asList(dishDtos);

        UnitDto expectedUnit = dish.ingredients().getFirst().unit();
        UnitDto actualUnit = dishes.getFirst().ingredients().getFirst().unit();

        Assertions.assertEquals(expectedUnit.name(), actualUnit.name());
        Assertions.assertEquals(expectedUnit.shortName(), actualUnit.shortName());
    }

    @Test
    void getDishFoundReturnsOk() throws Exception {
        DishDto dish = persistDish("Soup");

        mockMvc.perform(get("/api/menu/{id}", dish.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Soup"));
    }

    @Test
    void getDishNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/api/menu/{id}", 9999L))
                .andExpect(status().isNotFound());
    }

    @Test
    @Disabled
    void createReturnsCreatedDish() throws Exception {
        DishDto request = sampleDish("Soup");

        mockMvc.perform(post("/api/menu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Soup"));
    }

    @Test
    @Disabled
    void updateReturnsUpdatedDish() throws Exception {
        DishDto existing = persistDish("Original");
        DishDto request = sampleDish("Updated Salad");

        mockMvc.perform(put("/api/menu/{id}", existing.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Salad"));
    }

    @Test
    void softDeleteReturnsNoContent() throws Exception {
        DishDto dish = persistDish("Temp");

        mockMvc.perform(delete("/api/menu/{id}", dish.id()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/menu/{id}", dish.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void importMenuReturnsSummary() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "menu.xlsx", MediaType.MULTIPART_FORM_DATA_VALUE, new byte[]{1, 2});
        MenuImportService.MenuImportSummary summary = new MenuImportService.MenuImportSummary(1, 2, 3);
        when(menuImportService.importMenu(file)).thenReturn(summary);

        mockMvc.perform(multipart("/api/menu/import").file(file))
                .andExpect(status().isOk());

        verify(menuImportService).importMenu(file);
    }

    private DishDto persistDish(String title) {
        DishDto request = sampleDish(title);
        DishDto saved = dishService.createDish(request);
        return new DishDto(
                saved.id(),
                request.category(),
                request.title(),
                request.description(),
                request.active(),
                request.ingredients());
    }

    private DishDto sampleDish(String title) {
        Unit unit = ensureUnitExists();
        UnitDto unitDto = new UnitDto(unit.getId(), unit.getName(), unit.getShortName());
        DishIngredientDto ingredient = new DishIngredientDto(
                null,
                "Salt",
                BigDecimal.ONE,
                unit.getId(),
                unitDto,
                false,
                null);
        return new DishDto(
                null,
                "Main",
                title,
                null,
                true,
                List.of(ingredient));
    }

    private Unit ensureUnitExists() {
        return unitRepository.findAll().stream().findFirst().orElseGet(() -> {
            Unit unit = new Unit();
            unit.setId(UUID.randomUUID());
            unit.setName("Unit");
            unit.setShortName("u");
            return unitRepository.save(unit);
        });
    }
}
