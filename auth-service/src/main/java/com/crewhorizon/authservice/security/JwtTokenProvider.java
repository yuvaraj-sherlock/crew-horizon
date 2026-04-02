package com.crewhorizon.authservice.security;

import com.crewhorizon.authservice.entity.RoleEntity;
import com.crewhorizon.authservice.entity.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ============================================================
 * JWT Token Provider
 * ============================================================
 * WHAT: Generates, validates, and parses JWT access and refresh
 *       tokens for the CREW Horizon authentication system.
 *
 * WHY JWT (JSON Web Token) over Session-Based Auth:
 *       In a microservices architecture with horizontal scaling:
 *
 *       SESSION-BASED PROBLEMS:
 *       - Sessions stored in-memory are lost on pod restart
 *       - Shared session storage (Redis) creates coupling
 *       - Every API call requires a session DB lookup (latency)
 *       - Difficult to share sessions across multiple services
 *
 *       JWT ADVANTAGES:
 *       - STATELESS: Token contains all claims — no DB lookup
 *       - SELF-CONTAINED: Includes username, roles, expiry
 *       - SCALABLE: Any service instance can validate independently
 *       - PORTABLE: Works across domains (web, mobile, B2B APIs)
 *
 * WHY TWO TOKENS (Access + Refresh):
 *       Short-lived ACCESS tokens (15-60 min) minimize the damage
 *       if intercepted — they expire quickly.
 *       Long-lived REFRESH tokens (7 days) enable seamless UX
 *       without re-login, stored securely (httpOnly cookie or
 *       secure storage) and can be revoked in Redis.
 *
 * WHY HS256 (HMAC-SHA256):
 *       Symmetric signing is simpler for a single auth service.
 *       The secret key is shared only between auth-service and
 *       api-gateway (via K8s Secrets). For B2B or multi-tenant
 *       scenarios, RS256 (RSA asymmetric) would be preferred
 *       so downstream services only need the public key.
 * ============================================================
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${jwt.access-token.expiration-ms:900000}") long accessTokenExpirationMs,
            @Value("${jwt.refresh-token.expiration-ms:604800000}") long refreshTokenExpirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    /**
     * Generates a JWT access token for an authenticated user.
     *
     * WHY include roles in the token (not just username):
     * Authorization decisions in downstream services and the
     * gateway require role information. Including roles eliminates
     * the need for a "permission lookup" on every request — the
     * token IS the authorization artifact.
     *
     * WHY include jti (JWT ID) claim:
     * The jti uniquely identifies this token. If a token needs
     * to be revoked before expiry (logout, password change), we
     * store the jti in Redis blacklist and check on every request.
     *
     * @param user authenticated user entity
     * @return signed JWT access token string
     */
    public String generateAccessToken(UserEntity user) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(accessTokenExpirationMs);

        List<String> roles = user.getRoles().stream()
                .map(role -> role.getName().name())
                .collect(Collectors.toList());

        return Jwts.builder()
                .id(UUID.randomUUID().toString())     // jti: unique token ID for revocation
                .subject(user.getEmail())              // sub: standard claim for user identity
                .claim("employeeId", user.getEmployeeId())
                .claim("firstName", user.getFirstName())
                .claim("lastName", user.getLastName())
                .claim("roles", roles)                 // Custom claim: RBAC roles
                .claim("tokenType", "ACCESS")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .issuer("crew-horizon-auth-service")   // iss: identifies the token issuer
                .signWith(signingKey)
                .compact();
    }

    /**
     * Generates a refresh token.
     *
     * WHY refresh token contains fewer claims:
     * Refresh tokens are used ONLY to obtain new access tokens.
     * They should contain minimal claims to reduce exposure
     * if the refresh token is compromised.
     * The full user claims are re-fetched from DB when issuing
     * a new access token (catches role changes, account locks).
     */
    public String generateRefreshToken(UserEntity user) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(refreshTokenExpirationMs);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getEmail())
                .claim("tokenType", "REFRESH")
                .claim("employeeId", user.getEmployeeId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .issuer("crew-horizon-auth-service")
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validates a token and extracts all claims.
     *
     * WHY throw JwtException instead of returning null/Optional:
     * Null checks are easy to forget. Exceptions force the caller
     * to handle the failure case explicitly, preventing silent
     * security bypass bugs.
     */
    public Claims validateAndExtractClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractEmail(String token) {
        return validateAndExtractClaims(token).getSubject();
    }

    public String extractTokenId(String token) {
        return validateAndExtractClaims(token).getId();
    }

    public boolean isAccessToken(Claims claims) {
        return "ACCESS".equals(claims.get("tokenType", String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return "REFRESH".equals(claims.get("tokenType", String.class));
    }
}
