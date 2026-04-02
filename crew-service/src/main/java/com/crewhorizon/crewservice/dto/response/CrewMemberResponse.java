package com.crewhorizon.crewservice.dto.response;

import com.crewhorizon.crewservice.entity.CrewMemberEntity;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ============================================================
 * Crew Member Response DTO
 * ============================================================
 * WHAT: Output DTO returned to API consumers when querying
 *       crew member data. Contains ONLY safe, relevant fields.
 *
 * WHY different response shapes exist (full vs summary):
 *       - List endpoints: return CrewMemberSummaryResponse (lean)
 *         to avoid transferring unneeded data for each of
 *         potentially hundreds of results.
 *       - Detail endpoint: return CrewMemberResponse (complete)
 *         when a specific crew member's full profile is needed.
 *
 *       This follows the "Response Shaping" pattern — different
 *       API views of the same entity for different use cases,
 *       optimizing both network bandwidth and DB query complexity.
 *
 * WHY @JsonInclude(NON_NULL):
 *       Conditionally populated fields (qualifications, loaded
 *       only on detail view) are null in summary responses.
 *       NON_NULL prevents sending "qualifications: null" in
 *       summary responses, keeping the JSON compact.
 * ============================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CrewMemberResponse {
    private Long id;
    private String employeeId;
    private String firstName;
    private String lastName;
    private String fullName;  // Convenience field: firstName + " " + lastName
    private String email;
    private CrewMemberEntity.CrewType crewType;
    private CrewMemberEntity.DutyStatus dutyStatus;
    private String baseAirport;
    private Integer seniorityNumber;
    private LocalDate dateOfHire;
    private LocalDate dateOfBirth;
    private String licenseNumber;
    private LocalDate licenseExpiry;
    private LocalDate medicalCertificateExpiry;
    private String nationality;
    private LocalDate passportExpiry;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Loaded only in detail view (null in summary/list responses)
    private List<QualificationResponse> qualifications;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QualificationResponse {
        private Long id;
        private String aircraftType;
        private CrewQualificationResponse.QualificationTypeResponse qualificationType;
        private LocalDate issuedDate;
        private LocalDate expiryDate;
        private String issuingAuthority;
        private String certificateNumber;
        private Boolean isCurrent;
        private Boolean isExpired;
        private Boolean isExpiringSoon;

        public enum QualificationTypeResponse {
            TYPE_RATING, ROUTE_CHECK, LINE_CHECK, INSTRUMENT_RATING,
            DANGEROUS_GOODS, SAFETY_TRAINING, FIRST_AID, RECURRENT_TRAINING
        }
    }
}
