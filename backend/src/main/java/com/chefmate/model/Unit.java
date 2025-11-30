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
@Table(name = "unit")
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
public class Unit {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "short_name", nullable = false)
    private String shortName;

    @PrePersist
    public void ensureId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
