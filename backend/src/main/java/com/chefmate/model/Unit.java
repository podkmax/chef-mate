package com.chefmate.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "unit")
public class Unit {
    @Id
    public UUID id;

    @Column(nullable = false)
    public String name;

    @Column(name = "short_name", nullable = false)
    public String shortName;

    @PrePersist
    public void ensureId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
