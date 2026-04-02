package com.crewhorizon.authservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * CREW Horizon - Authentication Service
 * Central identity and access management for the platform.
 */
@SpringBootApplication
@EnableCaching
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
