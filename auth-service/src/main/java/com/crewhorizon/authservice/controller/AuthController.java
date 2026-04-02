package com.crewhorizon.authservice.controller;

import com.crewhorizon.authservice.dto.request.LoginRequest;
import com.crewhorizon.authservice.dto.request.RegisterRequest;
import com.crewhorizon.authservice.dto.response.AuthResponse;
import com.crewhorizon.authservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ============================================================
 * Auth Controller
 * ============================================================
 * WHAT: REST API endpoints for authentication operations.
 *       Acts as the HTTP entry point for all auth requests.
 *
 * WHY @RestController (vs @Controller):
 *       @RestController = @Controller + @ResponseBody.
 *       Every method return value is automatically serialized
 *       to JSON via Jackson. No @ResponseBody needed per method.
 *
 * WHY controllers are thin (delegating to service):
 *       Controllers handle HTTP concerns ONLY:
 *       - Request/response binding
 *       - Input validation (@Valid)
 *       - HTTP status codes
 *       - Documentation annotations (@Operation)
 *
 *       Business logic belongs in the Service layer. This
 *       separation enables testing business logic without
 *       spinning up the web layer (MockMvc vs unit test).
 *
 * WHY @Valid on @RequestBody:
 *       Triggers Bean Validation before the method body executes.
 *       Invalid inputs (missing fields, format violations) return
 *       HTTP 400 automatically via GlobalExceptionHandler,
 *       without polluting service code with validation logic.
 * ============================================================
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "CREW Horizon Authentication API")
public class AuthController {

    private final AuthService authService;

    /**
     * User login endpoint.
     *
     * WHY POST (not GET) for login:
     * Credentials must NEVER appear in the URL (GET query params
     * are logged in server logs, proxy logs, browser history).
     * POST sends credentials in the request body (over HTTPS).
     */
    @PostMapping("/login")
    @Operation(
            summary = "Authenticate user",
            description = "Authenticates crew member credentials and returns JWT tokens",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Login successful"),
                    @ApiResponse(responseCode = "401", description = "Invalid credentials"),
                    @ApiResponse(responseCode = "423", description = "Account locked"),
                    @ApiResponse(responseCode = "400", description = "Validation error")
            }
    )
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * User registration endpoint.
     * WHY 201 CREATED (not 200 OK):
     * HTTP 201 signals that a new resource was created.
     * This is the semantically correct response for POST operations
     * that create a new entity (user account).
     */
    @PostMapping("/register")
    @Operation(summary = "Register new crew member account")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Token refresh endpoint.
     *
     * WHY refresh token in request body (not Authorization header):
     * The refresh token is a DIFFERENT type of token from the access token.
     * Sending it as a JSON body field makes the endpoint semantically clear
     * and avoids confusion with the "Bearer" access token pattern.
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh token")
    public ResponseEntity<AuthResponse> refreshToken(
            @RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        AuthResponse response = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(response);
    }

    /**
     * Logout endpoint.
     *
     * WHY extract token from header (not body):
     * The access token is in the Authorization header on every
     * authenticated request. Logout should use the SAME token
     * that the user is currently authenticated with.
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout and invalidate current access token")
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            authService.logout(token);
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    /**
     * Health check / ping endpoint (public).
     * WHY: Simple connectivity test for the auth service.
     */
    @GetMapping("/ping")
    @Operation(summary = "Health check for auth service")
    public ResponseEntity<Map<String, String>> ping() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "auth-service",
                "timestamp", java.time.Instant.now().toString()
        ));
    }
}
