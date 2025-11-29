package com.chefmate.api;

import com.chefmate.AbstractApplicationTest;
import com.chefmate.dto.DishDto;
import com.chefmate.dto.DishIngredientDto;
import com.chefmate.service.MenuImportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Test
    void healthReturnsOk() throws Exception {
        mockMvc.perform(get("/api/menu/health"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string("menu-api-ok"));
    }

    @Test
    void getMenuReturnsDishes() throws Exception {
        DishDto dish = sampleDish();
        dish.id = 1L;
        dish.title = "Soup";
        when(dishService.getActiveDishes()).thenReturn(List.of(dish));

        mockMvc.perform(get("/api/menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title", equalTo("Soup")));

        verify(dishService).getActiveDishes();
    }

    @Test
    void getDishFoundReturnsOk() throws Exception {
        DishDto dish = sampleDish();
        dish.id = 1L;
        dish.title = "Soup";
        when(dishService.getDish(1L)).thenReturn(dish);

        mockMvc.perform(get("/api/menu/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", equalTo("Soup")));

        verify(dishService).getDish(1L);
    }

    @Test
    void getDishNotFoundReturns404() throws Exception {
        when(dishService.getDish(99L)).thenReturn(null);

        mockMvc.perform(get("/api/menu/{id}", 99L))
                .andExpect(status().isNotFound());

        verify(dishService).getDish(99L);
    }

    @Test
    void createReturnsCreatedDish() throws Exception {
        DishDto request = sampleDish();
        DishDto saved = sampleDish();
        saved.id = 10L;
        saved.title = "Soup";
        when(dishService.createDish(any(DishDto.class))).thenReturn(saved);

        mockMvc.perform(post("/api/menu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(10)));

        verify(dishService).createDish(any(DishDto.class));
    }

    @Test
    void updateReturnsUpdatedDish() throws Exception {
        DishDto request = sampleDish();
        request.title = "Salad";
        DishDto updated = sampleDish();
        updated.id = 5L;
        updated.title = "Updated Salad";
        when(dishService.updateDish(eq(5L), any(DishDto.class))).thenReturn(updated);

        mockMvc.perform(put("/api/menu/{id}", 5L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", equalTo("Updated Salad")));

        verify(dishService).updateDish(eq(5L), any(DishDto.class));
    }

    @Test
    void softDeleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/menu/{id}", 7L))
                .andExpect(status().isNoContent());

        verify(dishService).softDelete(7L);
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

    private DishDto sampleDish() {
        DishDto dish = new DishDto();
        dish.category = "Main";
        dish.title = "Dish";
        dish.active = true;
        DishIngredientDto ingredient = new DishIngredientDto();
        ingredient.name = "Salt";
        ingredient.qty = BigDecimal.ONE;
        ingredient.unitId = UUID.randomUUID();
        dish.ingredients = List.of(ingredient);
        return dish;
    }
}
