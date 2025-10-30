package com.chefmate.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long userId;
    public LocalDate targetDate;
    @Enumerated(EnumType.STRING)
    public OrderStatus status;
    public OffsetDateTime createdAt;
    public OffsetDateTime updatedAt;
    public String comment;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    public List<OrderItem> items;
}


