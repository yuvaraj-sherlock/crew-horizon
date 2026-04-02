package com.crewhorizon.rosterservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

/**
 * ============================================================
 * Roster Assignment Entity
 * ============================================================
 * WHAT: Represents a single crew member's assignment to a
 *       specific flight. This is the CORE entity of the
 *       crew scheduling domain.
 *
 * WHY Roster Service as a separate bounded context:
 *       Rostering sits at the INTERSECTION of crew and flight data.
 *       Rather than polluting either service:
 *       - crew-service owns crew profiles
 *       - flight-service owns flight schedules
 *       - roster-service owns ASSIGNMENTS (the relationship)
 *
 *       This is the classic "association table as service" DDD pattern.
 *       The roster-service is the authoritative source for:
 *       - Who is flying which flight
 *       - Duty time limit (FTL) calculations
 *       - Crew rest period enforcement
 *       - Compliance reporting
 *
 * WHY store employeeId and flightNumber (not FK to other services):
 *       In microservices, cross-service foreign keys don't exist
 *       at the DB level. Referential integrity is enforced at
 *       the application level (validate existence before creating
 *       assignment). Storing string identifiers enables queries
 *       without cross-service joins.
 * ============================================================
 */
@Entity
@Table(
        name = "roster_assignments",
        indexes = {
                @Index(name = "idx_roster_employee_id", columnList = "employee_id"),
                @Index(name = "idx_roster_flight_number", columnList = "flight_number"),
                @Index(name = "idx_roster_duty_date", columnList = "duty_date"),
                @Index(name = "idx_roster_status", columnList = "status"),
                // Composite index for common scheduling query
                @Index(name = "idx_roster_emp_date",
                        columnList = "employee_id, duty_date")
        },
        // Prevent double-booking: same crew member on same flight
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_roster_employee_flight",
                        columnNames = {"employee_id", "flight_number", "duty_date"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RosterAssignmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Cross-service reference to crew-service.
     * WHY NOT @ManyToOne: The crew member lives in a different
     * service/database. We store the business key and validate
     * via API call when creating assignments.
     */
    @Column(name = "employee_id", nullable = false, length = 20)
    private String employeeId;

    @Column(name = "flight_number", nullable = false, length = 10)
    private String flightNumber;

    /**
     * WHY store crew role on assignment (not just look up from profile):
     * On a given flight, a crew member may act in a DIFFERENT role
     * than their standard role (e.g., a CAPTAIN acting as INSTRUCTOR
     * on a check flight). Storing the assigned role captures the
     * actual duty performed.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "assigned_role", nullable = false, length = 30)
    private AssignedRole assignedRole;

    @Column(name = "duty_date", nullable = false)
    private LocalDate dutyDate;

    /**
     * WHY ZonedDateTime for duty times:
     * Same reason as FlightEntity — timezone accuracy for FTL calculations.
     * ICAO flight and duty time limitations (FTL) use actual clock times,
     * not just durations.
     */
    @Column(name = "report_time")
    private ZonedDateTime reportTime;  // When crew must report to airport

    @Column(name = "duty_start_time")
    private ZonedDateTime dutyStartTime;

    @Column(name = "duty_end_time")
    private ZonedDateTime dutyEndTime;

    @Column(name = "flight_time_minutes")
    private Integer flightTimeMinutes;  // Actual flight hours (for FTL tracking)

    @Column(name = "total_duty_minutes")
    private Integer totalDutyMinutes;   // Total duty period duration

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AssignmentStatus status = AssignmentStatus.DRAFT;

    @Column(name = "departure_airport", length = 3)
    private String departureAirport;

    @Column(name = "arrival_airport", length = 3)
    private String arrivalAirport;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "is_ftl_compliant")
    @Builder.Default
    private Boolean isFtlCompliant = true;

    @Column(name = "ftl_violation_reason", length = 255)
    private String ftlViolationReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "assigned_by", length = 100)
    private String assignedBy;

    public enum AssignedRole {
        CAPTAIN,
        FIRST_OFFICER,
        SECOND_OFFICER,
        PURSER,
        SENIOR_CABIN_CREW,
        CABIN_CREW,
        TRAINING_CAPTAIN,  // Line training role
        CHECK_CAPTAIN      // Proficiency check role
    }

    public enum AssignmentStatus {
        DRAFT,      // Created, pending validation
        PROPOSED,   // Sent to crew member for acknowledgment
        CONFIRMED,  // Crew member confirmed
        ACTIVE,     // Currently on duty
        COMPLETED,  // Flight completed
        CANCELLED,  // Assignment cancelled
        SWAPPED     // Replaced by a different crew member
    }
}
