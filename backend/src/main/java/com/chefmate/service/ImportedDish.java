package com.chefmate.service;

import java.util.ArrayList;
import java.util.List;

class ImportedDish {
    private final String name;
    private String description;
    private final List<ImportedIngredient> ingredients = new ArrayList<>();

    ImportedDish(String name) {
        this.name = name;
    }

    String getName() {
        return name;
    }

    String getDescription() {
        return description;
    }

    void setDescription(String description) {
        this.description = description;
    }

    List<ImportedIngredient> getIngredients() {
        return ingredients;
    }

    void addIngredient(ImportedIngredient ingredient) {
        ingredients.add(ingredient);
    }
}

