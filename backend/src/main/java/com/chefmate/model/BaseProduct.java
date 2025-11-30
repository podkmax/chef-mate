package com.chefmate.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "base_product")
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
public class BaseProduct {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String unit;

    @Column(nullable = false)
    private Boolean isFreezable = true;

    @PrePersist
    public void ensureId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
