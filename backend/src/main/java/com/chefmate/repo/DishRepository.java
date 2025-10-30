package com.chefmate.repo;

import com.chefmate.model.Dish;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DishRepository extends JpaRepository<Dish, Long> {
    List<Dish> findByActiveTrue();
    Optional<Dish> findByTitleIgnoreCase(String title);
}

