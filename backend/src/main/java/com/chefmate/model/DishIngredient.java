package com.chefmate.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "dish_ingredient")
@Getter
@Setter
@ToString(exclude = {"dish", "unit", "baseProduct"})
@EqualsAndHashCode(of = "id")
public class DishIngredient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dish_id", nullable = false)
    private Dish dish;

    private String name;
    private BigDecimal qty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    private Boolean excludeForClient = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_product_id", nullable = false)
    private BaseProduct baseProduct;
}
