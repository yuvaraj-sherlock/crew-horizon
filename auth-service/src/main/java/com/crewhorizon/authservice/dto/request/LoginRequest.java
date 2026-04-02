package com.crewhorizon.authservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * ============================================================
 * Auth Request DTOs
 * ============================================================
 * WHAT: Data Transfer Objects for authentication-related requests.
 *
 * WHY DTOs instead of exposing Entity directly in the API:
 *       1. SECURITY: Entity contains hashed password and internal
 *          fields that must never be returned to the client.
 *       2. API STABILITY: Entity schema can change (new DB columns)
 *          without breaking the API contract.
 *       3. VALIDATION: DTOs carry validation annotations specific
 *          to the API contract, not the DB schema.
 *       4. SERIALIZATION CONTROL: Jackson can serialize DTOs
 *          without risking recursive relationships or lazy-load
 *          exceptions (N+1 problem with JPA proxies).
 * ============================================================
 */
@Data
public class LoginRequest {

    /**
     * WHY @Email validation:
     * Fails fast at the input layer rather than hitting the DB
     * with an invalid email format. Also prevents SQL injection
     * patterns in the email field (though JPA uses parameterized
     * queries regardless).
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    private String email;

    /**
     * WHY @Size min=8:
     * Enforces minimum password complexity at the API layer.
     * The actual complexity rules (uppercase, digits, special chars)
     * are validated in the service layer for flexibility.
     */
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String password;
}
