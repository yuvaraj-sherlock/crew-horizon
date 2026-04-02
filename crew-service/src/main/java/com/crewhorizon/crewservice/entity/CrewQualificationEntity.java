package com.crewhorizon.crewservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ============================================================
 * Crew Qualification Entity
 * ============================================================
 * WHAT: Tracks aircraft type ratings, route qualifications,
 *       and regulatory certifications for crew members.
 *
 * WHY Separate Entity (not embedded in CrewMember):
 *       A crew member can have MULTIPLE qualifications (e.g.,
 *       rated on B737, A320, and A380). A collection of embedded
 *       objects would either use a JSON column (limited query
 *       ability) or a separate table anyway.
 *       Using a separate @Entity gives:
 *       - Individual CRUD on qualifications
 *       - Full querying capability (find all B737-rated crews)
 *       - Clean audit trail per qualification event
 *
 * WHY aircraft qualification tracking matters:
 *       Aviation regulations (ICAO Annex 1, EU OPS) mandate
 *       that crew are only assigned to aircraft they're rated for.
 *       This entity is what scheduling systems query to enforce
 *       legal compliance.
 * ============================================================
 */
@Entity
@Table(
        name = "crew_qualifications",
        indexes = {
                @Index(name = "idx_qual_crew_member", columnList = "crew_member_id"),
                @Index(name = "idx_qual_aircraft_type", columnList = "aircraft_type"),
                @Index(name = "idx_qual_expiry", columnList = "expiry_date")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrewQualificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * WHY @ManyToOne with LAZY fetch:
     * This is the "many" side of the relationship.
     * LAZY loading means the full CrewMemberEntity is not loaded
     * just because we load a qualification — avoids N+1 chains.
     * FetchType.LAZY is the Hibernate default for @ManyToOne,
     * but explicit declaration is clearer.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crew_member_id", nullable = false)
    private CrewMemberEntity crewMember;

    // e.g., "B737", "A320", "A380", "B777"
    @Column(name = "aircraft_type", nullable = false, length = 20)
    private String aircraftType;

    @Enumerated(EnumType.STRING)
    @Column(name = "qualification_type", nullable = false, length = 30)
    private QualificationType qualificationType;

    @Column(name = "issued_date", nullable = false)
    private LocalDate issuedDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "issuing_authority", length = 100)
    private String issuingAuthority;

    @Column(name = "certificate_number", length = 50)
    private String certificateNumber;

    @Column(name = "is_current")
    @Builder.Default
    private Boolean isCurrent = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public boolean isExpired() {
        return expiryDate != null && expiryDate.isBefore(LocalDate.now());
    }

    public boolean isExpiringSoon(int daysThreshold) {
        return expiryDate != null &&
               !isExpired() &&
               expiryDate.isBefore(LocalDate.now().plusDays(daysThreshold));
    }

    public enum QualificationType {
        TYPE_RATING,          // Aircraft type rating
        ROUTE_CHECK,          // Route-specific qualification
        LINE_CHECK,           // Annual proficiency check
        INSTRUMENT_RATING,    // IFR qualification
        DANGEROUS_GOODS,      // IATA DG handling certification
        SAFETY_TRAINING,      // Emergency procedures
        FIRST_AID,            // Medical first responder
        RECURRENT_TRAINING    // Annual recurrent training
    }
}
