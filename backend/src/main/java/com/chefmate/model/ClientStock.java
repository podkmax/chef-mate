package com.chefmate.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "client_stock")
public class ClientStock {
    @Id
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    public User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "base_product_id", nullable = false)
    public BaseProduct baseProduct;

    @Column(nullable = false, precision = 12, scale = 3)
    public BigDecimal qty;

    @Column(nullable = false)
    public String unit;

    @PrePersist
    public void ensureId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
