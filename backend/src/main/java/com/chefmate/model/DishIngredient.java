package com.chefmate.model;

import jakarta.persistence.*;
import java.math.BigDecimal;


@Entity
@Table(name = "dish_ingredient")
public class DishIngredient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dish_id", nullable = false)
    public Dish dish;
    public String name;
    public BigDecimal qty;
    public String unit;
    public Boolean excludeForClient = false;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_product_id", nullable = false)
    public BaseProduct baseProduct;
}

