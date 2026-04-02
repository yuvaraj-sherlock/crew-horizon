package com.crewhorizon.apigateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * ============================================================
 * JWT Token Validator - API Gateway
 * ============================================================
 * WHAT: Validates incoming JWT tokens at the gateway edge.
 *       Extracts claims (username, roles) from the token payload.
 *
 * WHY:  Performing JWT validation at the GATEWAY level (not in
 *       each individual service) follows the "Edge Security"
 *       principle:
 *
 *       1. PERFORMANCE: Downstream services skip auth logic
 *          entirely — they trust the gateway has already
 *          validated the token and forwarded enriched headers.
 *
 *       2. CONSISTENCY: All services use one validation strategy.
 *          No risk of a service accidentally skipping validation.
 *
 *       3. STATELESS: JWT tokens are self-contained — no DB
 *          lookup needed to validate (unlike session tokens).
 *          This is essential for horizontal scaling in K8s.
 *
 *       4. HMAC-SHA256 SIGNING: We use a symmetric secret key
 *          (HS256). For higher security requirements, RS256
 *          (asymmetric) can be adopted with a public/private
 *          key pair — the gateway holds the public key only.
 * ============================================================
 */
@Slf4j
@Component
public class JwtTokenValidator {

    private final SecretKey signingKey;

    /**
     * WHY @Value from environment/ConfigMap:
     * Never hardcode secrets in source code. In Kubernetes,
     * this value comes from a K8s Secret mounted as an env var,
     * managed by secrets management (e.g., Vault, AWS Secrets Manager).
     */
    public JwtTokenValidator(@Value("${jwt.secret}") String jwtSecret) {
        // WHY Keys.hmacShaKeyFor: Ensures the key meets minimum
        // HMAC-SHA256 length requirements (256 bits / 32 bytes).
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Validates a JWT token and returns its claims.
     *
     * WHY returning Claims instead of boolean:
     * The gateway needs to extract username and roles to forward
     * as headers to downstream services. A simple boolean
     * would require parsing the token twice.
     *
     * @param token raw JWT string (without "Bearer " prefix)
     * @return parsed Claims if valid
     * @throws JwtException if token is invalid or expired
     */
    public Claims validateAndExtractClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired for subject: {}", e.getClaims().getSubject());
            throw e;
        } catch (JwtException e) {
            log.error("JWT token validation failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Extracts roles from token claims.
     *
     * WHY roles in JWT:
     * Embedding roles in the token enables authorization decisions
     * at the gateway WITHOUT any downstream service call or DB lookup.
     * This is the foundation of stateless RBAC in microservices.
     */
    @SuppressWarnings("unchecked")
    public List<String> extractRoles(Claims claims) {
        Object roles = claims.get("roles");
        if (roles instanceof List) {
            return (List<String>) roles;
        }
        return List.of();
    }

    public String extractUsername(Claims claims) {
        return claims.getSubject();
    }

    public boolean isTokenExpired(Claims claims) {
        return claims.getExpiration().before(new java.util.Date());
    }
}
