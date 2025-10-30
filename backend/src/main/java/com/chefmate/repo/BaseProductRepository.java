package com.chefmate.repo;

import com.chefmate.model.BaseProduct;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BaseProductRepository extends JpaRepository<BaseProduct, UUID> {
    Optional<BaseProduct> findByNameIgnoreCase(String name);
}
