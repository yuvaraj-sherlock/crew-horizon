package com.crewhorizon.flightservice;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;
/**
 * CREW Horizon - Flight Service
 * WHY standalone service: Flight data (schedules, routes, aircraft assignments)
 * changes independently from crew data. Separate service enables:
 * - Independent scaling during peak booking periods
 * - Independent deployment of flight schedule updates
 * - Clear data ownership (flight bounded context)
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
public class FlightServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlightServiceApplication.class, args);
    }
}
