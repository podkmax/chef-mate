package com.chefmate.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "base_product")
public class BaseProduct {
    @Id
    public UUID id;

    @Column(nullable = false, unique = true)
    public String name;

    @Column(nullable = false)
    public String unit;

    @Column(nullable = false)
    public Boolean isFreezable = true;

    @PrePersist
    public void ensureId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
