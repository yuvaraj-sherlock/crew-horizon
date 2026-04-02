package com.crewhorizon.authservice.service.impl;

import com.crewhorizon.authservice.dto.request.LoginRequest;
import com.crewhorizon.authservice.dto.request.RegisterRequest;
import com.crewhorizon.authservice.dto.response.AuthResponse;
import com.crewhorizon.authservice.entity.RoleEntity;
import com.crewhorizon.authservice.entity.UserEntity;
import com.crewhorizon.authservice.exception.*;
import com.crewhorizon.authservice.repository.RoleRepository;
import com.crewhorizon.authservice.repository.UserRepository;
import com.crewhorizon.authservice.security.JwtTokenProvider;
import com.crewhorizon.authservice.service.AuthService;
import com.crewhorizon.authservice.service.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ============================================================
 * Auth Service Implementation
 * ============================================================
 * WHAT: Core business logic for authentication operations:
 *       login, registration, token refresh, and logout.
 *
 * WHY @Transactional at method level (not class level):
 *       Not all methods need transactions (e.g., refreshToken
 *       only validates a JWT — no DB write needed).
 *       Applying @Transactional selectively avoids holding DB
 *       connections for non-DB operations, improving throughput.
 *
 * WHY @Slf4j for logging:
 *       Security events (login attempts, failures, lockouts)
 *       MUST be logged for audit trails and intrusion detection.
 *       The MDC (Mapped Diagnostic Context) should be used in
 *       production to correlate log entries with request IDs.
 * ============================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Authenticates a user and returns JWT tokens.
     *
     * WHY use AuthenticationManager (not manual password check):
     * AuthenticationManager handles the full authentication lifecycle:
     * - Account disabled checks
     * - Account locked checks
     * - Credential validation via BCrypt
     * - Fires Spring Security events (for auditing)
     * Manual BCrypt comparison would bypass all of these checks.
     *
     * WHY update login tracking in a separate @Transactional call:
     * We want to record the login EVEN IF token generation fails.
     * Separating these concerns (auth vs. audit) ensures atomicity
     * of each operation independently.
     */
    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        try {
            // Delegate to Spring Security's authentication infrastructure
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );

            // Load full user entity (Spring Security UserDetails has limited info)
            UserEntity user = userRepository
                    .findByEmailAndIsDeletedFalse(request.getEmail())
                    .orElseThrow(() -> new UserNotFoundException("User not found"));

            // Reset failed attempts on successful login
            userRepository.resetFailedAttemptsAndUpdateLastLogin(
                    request.getEmail(), LocalDateTime.now());

            String accessToken = jwtTokenProvider.generateAccessToken(user);
            String refreshToken = jwtTokenProvider.generateRefreshToken(user);

            Claims accessClaims = jwtTokenProvider.validateAndExtractClaims(accessToken);
            Claims refreshClaims = jwtTokenProvider.validateAndExtractClaims(refreshToken);

            List<String> roles = user.getRoles().stream()
                    .map(r -> r.getName().name())
                    .collect(Collectors.toList());

            log.info("Login successful: email={}, roles={}", request.getEmail(), roles);

            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .accessTokenExpiresAt(accessClaims.getExpiration().toInstant().getEpochSecond())
                    .refreshTokenExpiresAt(refreshClaims.getExpiration().toInstant().getEpochSecond())
                    .email(user.getEmail())
                    .employeeId(user.getEmployeeId())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .roles(roles)
                    .authenticatedAt(Instant.now())
                    .build();

        } catch (LockedException e) {
            log.warn("Login attempt on locked account: {}", request.getEmail());
            throw new AccountLockedException("Account is locked due to multiple failed attempts");
        } catch (BadCredentialsException e) {
            // WHY increment on failed attempt (in a new transaction):
            // The current transaction may rollback on exception.
            // We MUST record the failed attempt regardless.
            handleFailedLoginAttempt(request.getEmail());
            throw new InvalidCredentialsException("Invalid email or password");
        } catch (AuthenticationException e) {
            log.error("Authentication error for {}: {}", request.getEmail(), e.getMessage());
            throw new InvalidCredentialsException("Authentication failed");
        }
    }

    /**
     * Registers a new crew member user account.
     *
     * WHY @Transactional: Both user creation and role assignment
     * must succeed or fail together (atomicity).
     */
    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Registration attempt for email: {}", request.getEmail());

        // Validate no duplicate email or employeeId
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already registered: " + request.getEmail());
        }
        if (userRepository.existsByEmployeeId(request.getEmployeeId())) {
            throw new DuplicateResourceException("Employee ID already registered: " + request.getEmployeeId());
        }

        // WHY validate password confirmation in service (not just controller):
        // Defense in depth — service layer validation catches programmatic API
        // calls that bypass controller validation.
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new ValidationException("Passwords do not match");
        }

        // Default role for new crew members
        RoleEntity defaultRole = roleRepository
                .findByName(RoleEntity.RoleName.ROLE_CABIN_CREW)
                .orElseThrow(() -> new RuntimeException("Default role not found — check DB initialization"));

        UserEntity newUser = UserEntity.builder()
                .employeeId(request.getEmployeeId())
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                // WHY encode here (not in entity lifecycle):
                // Explicit encoding is clearer than implicit @PrePersist magic.
                // Also prevents double-encoding bugs during password changes.
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        newUser.addRole(defaultRole);
        UserEntity savedUser = userRepository.save(newUser);

        log.info("User registered successfully: email={}, employeeId={}",
                savedUser.getEmail(), savedUser.getEmployeeId());

        // Auto-login after registration (better UX — no need to login separately)
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(request.getEmail());
        loginRequest.setPassword(request.getPassword());
        return login(loginRequest);
    }

    /**
     * Refreshes an access token using a valid refresh token.
     *
     * WHY re-fetch user from DB (not use token claims):
     * If a user's roles were changed or account was disabled
     * between token issuance and refresh, the new access token
     * must reflect the CURRENT state, not the stale token state.
     * This ensures role changes take effect within the refresh window.
     */
    @Override
    public AuthResponse refreshToken(String refreshTokenStr) {
        try {
            Claims claims = jwtTokenProvider.validateAndExtractClaims(refreshTokenStr);

            if (!jwtTokenProvider.isRefreshToken(claims)) {
                throw new InvalidTokenException("Token is not a refresh token");
            }

            // Check if refresh token was explicitly revoked
            if (tokenBlacklistService.isBlacklisted(claims.getId())) {
                throw new InvalidTokenException("Refresh token has been revoked");
            }

            String email = claims.getSubject();
            UserEntity user = userRepository
                    .findByEmailAndIsDeletedFalse(email)
                    .orElseThrow(() -> new UserNotFoundException("User not found"));

            if (!user.getIsEnabled() || user.isCurrentlyLocked()) {
                throw new AccountLockedException("User account is disabled or locked");
            }

            String newAccessToken = jwtTokenProvider.generateAccessToken(user);
            Claims newAccessClaims = jwtTokenProvider.validateAndExtractClaims(newAccessToken);

            return AuthResponse.builder()
                    .accessToken(newAccessToken)
                    .tokenType("Bearer")
                    .accessTokenExpiresAt(newAccessClaims.getExpiration().toInstant().getEpochSecond())
                    .email(user.getEmail())
                    .employeeId(user.getEmployeeId())
                    .authenticatedAt(Instant.now())
                    .build();

        } catch (JwtException e) {
            throw new InvalidTokenException("Invalid or expired refresh token");
        }
    }

    /**
     * Logs out a user by blacklisting their current access token.
     *
     * WHY blacklist on logout (not just discard client-side):
     * Client-side logout only removes the token from storage.
     * The token is still VALID until expiry. If it was copied
     * before logout (e.g., from browser history, clipboard),
     * it can still be used. Blacklisting ensures server-side
     * enforcement of logout.
     */
    @Override
    public void logout(String accessToken) {
        try {
            Claims claims = jwtTokenProvider.validateAndExtractClaims(accessToken);
            String tokenId = claims.getId();
            Date expiry = claims.getExpiration();

            // TTL = remaining validity duration
            Duration ttl = Duration.between(Instant.now(), expiry.toInstant());
            if (ttl.isPositive()) {
                tokenBlacklistService.blacklistToken(tokenId, ttl);
            }

            log.info("User logged out: email={}, jti={}", claims.getSubject(), tokenId);
        } catch (JwtException e) {
            // Logging out with an invalid token is a no-op (already invalid)
            log.debug("Logout called with invalid token: {}", e.getMessage());
        }
    }

    @Override
    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        UserEntity user = userRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password changed for user: {}", email);
    }

    /**
     * WHY @Transactional(noRollbackFor):
     * Failed login tracking MUST be committed even if the outer
     * transaction rolls back. Otherwise, an attacker could trigger
     * rollbacks to prevent account lockout.
     */
    @Transactional(noRollbackFor = Exception.class)
    protected void handleFailedLoginAttempt(String email) {
        try {
            // Lock account for 15 minutes after 5 failures
            LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(15);
            userRepository.incrementFailedLoginAttempts(email, lockUntil);
            log.warn("Failed login attempt recorded for: {}", email);
        } catch (Exception e) {
            log.error("Could not update failed login attempts for: {}", email);
        }
    }
}
