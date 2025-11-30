package com.chefmate.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "telegram.bot")
public class BotProperties {
    private String token;
    private String username;
    private long cookId;
}
