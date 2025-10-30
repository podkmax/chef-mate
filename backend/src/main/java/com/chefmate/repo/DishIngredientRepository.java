package com.chefmate.repo;

import com.chefmate.model.DishIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DishIngredientRepository extends JpaRepository<DishIngredient, Long> {
}


