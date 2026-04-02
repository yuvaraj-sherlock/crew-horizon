package com.crewhorizon.authservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * ============================================================
 * Authentication Response DTO
 * ============================================================
 * WHAT: Encapsulates the response returned after a successful
 *       authentication (login or token refresh).
 *
 * WHY @JsonInclude(NON_NULL):
 *       Fields that are null won't appear in the JSON output.
 *       For example, 'refreshToken' might be omitted in certain
 *       contexts (e.g., machine-to-machine OAuth flows).
 *       This keeps the response payload clean and minimal.
 *
 * WHY return token expiry as epoch seconds:
 *       Frontend clients use the expiry to proactively refresh
 *       tokens before they expire (avoiding 401 mid-operation).
 *       Epoch seconds are timezone-independent and easy to compare
 *       with JavaScript's Date.now().
 * ============================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    private String accessToken;
    private String refreshToken;

    @Builder.Default
    private String tokenType = "Bearer";

    private long accessTokenExpiresAt;   // Unix epoch seconds
    private long refreshTokenExpiresAt;  // Unix epoch seconds

    // User identity info — avoids an extra /me API call post-login
    private String email;
    private String employeeId;
    private String firstName;
    private String lastName;
    private List<String> roles;

    private Instant authenticatedAt;
}
