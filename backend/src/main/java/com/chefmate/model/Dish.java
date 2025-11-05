package com.chefmate.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.List;

@Entity
@Table(name = "dish")
public class Dish {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String category;
    public String title;
    public String description;
    public Boolean active = true;
    public OffsetDateTime createdAt;
    public OffsetDateTime updatedAt;
    
    @OneToMany(mappedBy = "dish", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    public List<DishIngredient> ingredients;
}

