package com.crewhorizon.flightservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

/**
 * ============================================================
 * Flight Entity
 * ============================================================
 * WHAT: Represents a scheduled flight operation within the
 *       CREW Horizon flight management bounded context.
 *
 * WHY ZonedDateTime for flight times (not LocalDateTime):
 *       Airlines operate GLOBALLY across timezones. A flight
 *       departing DXB at 14:00 Gulf Standard Time and landing
 *       at LHR at 18:30 BST must store timezone information.
 *       ZonedDateTime preserves this. LocalDateTime would create
 *       ambiguity and potentially dangerous scheduling errors
 *       (crew assigned wrong rest periods due to timezone bugs).
 *
 * WHY flightNumber as a unique business key:
 *       Flight numbers (e.g., EK001) are universally understood
 *       by operations staff. They're used as lookup keys in
 *       scheduling systems, allowing cross-service queries
 *       without exposing internal DB IDs.
 * ============================================================
 */
@Entity
@Table(
        name = "flights",
        indexes = {
                @Index(name = "idx_flight_number_date",
                        columnList = "flight_number, scheduled_departure"),
                @Index(name = "idx_flight_departure", columnList = "departure_airport"),
                @Index(name = "idx_flight_arrival", columnList = "arrival_airport"),
                @Index(name = "idx_flight_status", columnList = "status"),
                @Index(name = "idx_flight_aircraft_type", columnList = "aircraft_type")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlightEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flight_number", nullable = false, length = 10)
    private String flightNumber;

    // IATA 3-letter airport codes
    @Column(name = "departure_airport", nullable = false, length = 3)
    private String departureAirport;

    @Column(name = "arrival_airport", nullable = false, length = 3)
    private String arrivalAirport;

    /**
     * WHY ZonedDateTime:
     * Stores departure/arrival with timezone information.
     * Serialized to DB as TIMESTAMP WITH TIME ZONE in PostgreSQL.
     * Essential for accurate duty time limit (FTL) calculations.
     */
    @Column(name = "scheduled_departure", nullable = false)
    private ZonedDateTime scheduledDeparture;

    @Column(name = "scheduled_arrival", nullable = false)
    private ZonedDateTime scheduledArrival;

    @Column(name = "actual_departure")
    private ZonedDateTime actualDeparture;

    @Column(name = "actual_arrival")
    private ZonedDateTime actualArrival;

    /**
     * WHY store aircraft type on the flight:
     * Crew assignment validation requires knowing which aircraft
     * type is used. Storing it here (denormalized from aircraft
     * registration) avoids cross-service lookups during scheduling.
     */
    @Column(name = "aircraft_type", nullable = false, length = 20)
    private String aircraftType;

    @Column(name = "aircraft_registration", length = 10)
    private String aircraftRegistration;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private FlightStatus status = FlightStatus.SCHEDULED;

    // Crew requirements for this flight
    @Column(name = "required_pilots")
    @Builder.Default
    private Integer requiredPilots = 2;

    @Column(name = "required_cabin_crew")
    @Builder.Default
    private Integer requiredCabinCrew = 4;

    // Flight duration in minutes (derived but stored for query efficiency)
    @Column(name = "planned_duration_minutes")
    private Integer plannedDurationMinutes;

    @Column(name = "delay_minutes")
    @Builder.Default
    private Integer delayMinutes = 0;

    @Column(name = "delay_reason", length = 255)
    private String delayReason;

    @Column(name = "is_cancelled")
    @Builder.Default
    private Boolean isCancelled = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    public enum FlightStatus {
        SCHEDULED,     // Future flight, crew not yet assigned
        CREW_ASSIGNED, // Crew assignment complete
        CONFIRMED,     // All checks passed, flight confirmed
        BOARDING,      // Passengers boarding
        AIRBORNE,      // In flight
        LANDED,        // Arrived at destination
        COMPLETED,     // Flight closed (all post-flight tasks done)
        DELAYED,       // Departure delayed
        CANCELLED,     // Flight cancelled
        DIVERTED       // Diverted to alternate airport
    }
}
