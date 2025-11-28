package com.chefmate.repo;

import com.chefmate.model.DishIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DishIngredientRepository extends JpaRepository<DishIngredient, Long> {

    @Modifying(clearAutomatically = true)
    @Query("delete from DishIngredient di where di.dish.id = :dishId")
    void deleteByDishId(@Param("dishId") Long dishId);
}
