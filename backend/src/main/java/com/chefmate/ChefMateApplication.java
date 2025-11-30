package com.chefmate;

import com.chefmate.config.BotProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(BotProperties.class)
public class ChefMateApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChefMateApplication.class, args);
    }
}

