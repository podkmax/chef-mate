package com.chefmate.repo;

import com.chefmate.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByTelegramId(Long telegramId);
    List<User> findByRoleIgnoreCase(String role);
}

