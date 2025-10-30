package com.chefmate.repo;

import com.chefmate.model.ClientStock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClientStockRepository extends JpaRepository<ClientStock, UUID> {
    List<ClientStock> findByUserId(Long userId);
    Optional<ClientStock> findByIdAndUserId(UUID id, Long userId);
    Optional<ClientStock> findByUserIdAndBaseProductId(Long userId, UUID baseProductId);
}
