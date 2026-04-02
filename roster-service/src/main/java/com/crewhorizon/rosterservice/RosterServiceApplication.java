package com.crewhorizon.rosterservice;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
/**
 * CREW Horizon - Roster Service
 * WHY standalone service: Rostering is the most complex domain —
 * it orchestrates crew + flight data. Keeping it separate means:
 * - Roster engine can be scaled independently (CPU-intensive)
 * - Roster rules (FTL compliance, rest periods) are isolated
 * - Other services are unaffected by roster computation load
 */
@SpringBootApplication
@EnableCaching
public class RosterServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RosterServiceApplication.class, args);
    }
}
