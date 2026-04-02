package com.crewhorizon.crewservice.dto.request;

import com.crewhorizon.crewservice.entity.CrewMemberEntity;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

/**
 * ============================================================
 * Crew Member Request DTOs
 * ============================================================
 * WHAT: Input DTOs for creating and updating crew member profiles.
 *
 * WHY DTO vs Entity in request:
 *       Receiving an Entity object directly in a controller allows
 *       clients to set internal fields (id, createdAt, isDeleted)
 *       — a "mass assignment" security vulnerability. DTOs
 *       expose ONLY the fields clients are permitted to set.
 * ============================================================
 */
@Data
public class CreateCrewMemberRequest {

    @NotBlank(message = "Employee ID is required")
    @Pattern(regexp = "^[A-Z]{2}[0-9]{6}$",
            message = "Employee ID format: 2 uppercase letters + 6 digits")
    private String employeeId;

    @NotBlank(message = "First name is required")
    @Size(min = 1, max = 100)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 1, max = 100)
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email
    private String email;

    @NotNull(message = "Crew type is required")
    private CrewMemberEntity.CrewType crewType;

    /**
     * WHY @Pattern for IATA airport code:
     * Base airport must be a valid 3-letter IATA code.
     * Invalid codes would cause silent data quality issues
     * in scheduling queries (no crew found for unknown airport).
     */
    @NotBlank(message = "Base airport is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Base airport must be a 3-letter IATA code (e.g., DXB)")
    private String baseAirport;

    @NotNull(message = "Date of hire is required")
    @PastOrPresent(message = "Date of hire cannot be in the future")
    private LocalDate dateOfHire;

    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @Size(max = 50)
    private String licenseNumber;

    private LocalDate licenseExpiry;

    private LocalDate medicalCertificateExpiry;

    @Size(max = 3)
    private String nationality;

    @Size(max = 20)
    private String passportNumber;

    private LocalDate passportExpiry;
}
