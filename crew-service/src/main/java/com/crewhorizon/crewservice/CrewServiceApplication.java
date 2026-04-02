package com.crewhorizon.crewservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * CREW Horizon - Crew Management Service
 * Owns the crew member bounded context.
 */
@SpringBootApplication
@EnableCaching
public class CrewServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CrewServiceApplication.class, args);
    }
}
