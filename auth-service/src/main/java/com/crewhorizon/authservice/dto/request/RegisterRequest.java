package com.crewhorizon.authservice.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * Registration request DTO.
 *
 * WHY pattern validation on employeeId:
 * Employee IDs follow airline-specific formats. Regex validation
 * rejects obviously invalid IDs before any DB interaction.
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "Employee ID is required")
    @Pattern(regexp = "^[A-Z]{2}[0-9]{6}$",
            message = "Employee ID must be 2 uppercase letters followed by 6 digits (e.g., EK123456)")
    private String employeeId;

    @NotBlank(message = "First name is required")
    @Size(min = 1, max = 100)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 1, max = 100)
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    private String email;

    /**
     * WHY password complexity requirements:
     * Aviation systems handle sensitive operational data.
     * Strong password policies are mandated by IATA cybersecurity
     * guidelines and airline security audits.
     */
    @NotBlank(message = "Password is required")
    @Size(min = 12, max = 100, message = "Password must be at least 12 characters")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=!]).*$",
            message = "Password must contain: uppercase, lowercase, digit, and special character"
    )
    private String password;

    @NotBlank(message = "Password confirmation is required")
    private String confirmPassword;
}
