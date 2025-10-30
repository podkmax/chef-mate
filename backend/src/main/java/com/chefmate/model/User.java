package com.chefmate.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "user_account")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long telegramId;
    public String name;
    public String role;
    public OffsetDateTime createdAt;
}


