package com.crewhorizon.crewservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================
 * CrewMember Entity
 * ============================================================
 * WHAT: JPA entity representing a crew member profile within
 *       the Crew Service bounded context.
 *
 * WHY SEPARATE from auth-service UserEntity:
 *       Auth service owns IDENTITY (who you are — email, password).
 *       Crew service owns PROFESSIONAL PROFILE (what you do —
 *       qualifications, licenses, seniority, duty status).
 *
 *       This separation means:
 *       - A crew member can have a rich professional profile
 *         without coupling it to authentication concerns
 *       - Auth service can be replaced (e.g., with Keycloak)
 *         without touching crew data
 *       - Crew service DB can scale independently of auth DB
 *
 * WHY @Index annotations:
 *       Query patterns drive index design. We index on:
 *       - employeeId: most common lookup key (scheduling systems)
 *       - crewType: filter queries (e.g., "show all PILOTs")
 *       - baseAirport + dutyStatus: rostering queries
 *       These prevent full table scans on common query patterns.
 * ============================================================
 */
@Entity
@Table(
        name = "crew_members",
        indexes = {
                @Index(name = "idx_crew_employee_id", columnList = "employee_id", unique = true),
                @Index(name = "idx_crew_type", columnList = "crew_type"),
                @Index(name = "idx_crew_base_airport", columnList = "base_airport"),
                @Index(name = "idx_crew_duty_status", columnList = "duty_status"),
                @Index(name = "idx_crew_base_status", columnList = "base_airport, duty_status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrewMemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * WHY employeeId as the cross-service identity key:
     * This is the shared identifier between auth-service and
     * crew-service. We store it here to look up crew profiles
     * from JWT claims (which carry employeeId) without
     * calling back to the auth service.
     */
    @Column(name = "employee_id", nullable = false, unique = true, length = 20)
    private String employeeId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /**
     * WHY @Enumerated(STRING): See explanation in RoleEntity.
     * Enum stability > ordinal fragility.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "crew_type", nullable = false, length = 30)
    private CrewType crewType;

    @Enumerated(EnumType.STRING)
    @Column(name = "duty_status", nullable = false, length = 30)
    @Builder.Default
    private DutyStatus dutyStatus = DutyStatus.AVAILABLE;

    // IATA airport code (e.g., DXB, JFK, LHR)
    @Column(name = "base_airport", nullable = false, length = 3)
    private String baseAirport;

    @Column(name = "seniority_number")
    private Integer seniorityNumber;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "date_of_hire", nullable = false)
    private LocalDate dateOfHire;

    @Column(name = "license_number", length = 50)
    private String licenseNumber;

    @Column(name = "license_expiry")
    private LocalDate licenseExpiry;

    @Column(name = "medical_certificate_expiry")
    private LocalDate medicalCertificateExpiry;

    @Column(name = "nationality", length = 3)  // ISO 3166-1 alpha-3
    private String nationality;

    @Column(name = "passport_number", length = 20)
    private String passportNumber;

    @Column(name = "passport_expiry")
    private LocalDate passportExpiry;

    /**
     * WHY @OneToMany with CascadeType.ALL:
     * Qualifications are owned by the crew member. When a crew
     * member is deleted (soft), their qualifications should also
     * be "deleted". CascadeType.ALL propagates lifecycle operations.
     *
     * WHY orphanRemoval = true:
     * If a qualification is removed from the collection, it should
     * be deleted from the DB automatically (orphan cleanup).
     *
     * WHY LAZY fetch:
     * Qualifications are large and only needed in specific contexts
     * (qualification verification, not basic crew lookup).
     * LAZY prevents loading them on every crew query.
     */
    @OneToMany(mappedBy = "crewMember",
               cascade = CascadeType.ALL,
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    @Builder.Default
    private List<CrewQualificationEntity> qualifications = new ArrayList<>();

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    /**
     * Crew types in an airline operations context.
     * WHY enum for crew type:
     * Crew type determines scheduling rules, duty time limits,
     * and legal compliance requirements. It must be strictly
     * controlled, not free-form text.
     */
    public enum CrewType {
        CAPTAIN,
        FIRST_OFFICER,
        SECOND_OFFICER,
        FLIGHT_ENGINEER,
        PURSER,
        SENIOR_CABIN_CREW,
        CABIN_CREW,
        TRAINEE
    }

    /**
     * Operational duty status for rostering decisions.
     */
    public enum DutyStatus {
        AVAILABLE,        // Can be assigned to flights
        ON_DUTY,          // Currently on a flight or at standby
        ON_LEAVE,         // Annual/sick leave
        TRAINING,         // Simulator or classroom training
        REST_PERIOD,      // Mandatory post-flight rest (FTL compliance)
        MEDICAL_LEAVE,    // Medical grounding
        SUSPENDED,        // Administrative suspension
        UNAVAILABLE       // General unavailability
    }
}
