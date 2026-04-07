package com.crewhorizon.crewservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ============================================================
 * JWT Request Filter — Downstream Services
 * ============================================================
 * WHAT: Validates JWT tokens in downstream microservices
 *       (crew-service, flight-service, roster-service, etc.)
 *       for defense-in-depth security.
 *
 * TWO MODES OF OPERATION:
 *
 * Mode 1 — Header-based (from API Gateway):
 *   The gateway validates the JWT and forwards:
 *   - X-Authenticated-User: "ahmed@crew-horizon.com"
 *   - X-User-Roles: "ROLE_PILOT,ROLE_CABIN_CREW"
 *   The service trusts these headers (enforced by K8s network policy)
 *   and builds the SecurityContext from them WITHOUT re-parsing JWT.
 *   This is the FAST PATH (no crypto operations).
 *
 * Mode 2 — Direct JWT validation (defense-in-depth):
 *   If no gateway headers, fall back to parsing the Bearer token.
 *   This handles direct API calls (testing, internal services)
 *   that bypass the gateway.
 *
 * WHY both modes:
 *   In production K8s, all traffic goes through the gateway.
 *   But during integration testing, developers call services
 *   directly without the gateway. Mode 2 keeps this working.
 * ============================================================
 */
@Slf4j
@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    private final SecretKey signingKey;

    public JwtRequestFilter(@Value("${jwt.secret}") String jwtSecret) {
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // ─── Mode 1: Trust gateway-forwarded headers (fast path) ──────────
        String gatewayUser = request.getHeader("X-Authenticated-User");
        String gatewayRoles = request.getHeader("X-User-Roles");

        if (StringUtils.hasText(gatewayUser) && StringUtils.hasText(gatewayRoles)
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            List<SimpleGrantedAuthority> authorities = Arrays
                    .stream(gatewayRoles.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(gatewayUser, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);

            log.debug("Authenticated via gateway headers: user={}, roles={}", gatewayUser, gatewayRoles);
            filterChain.doFilter(request, response);
            return;
        }

        // ─── Mode 2: Direct JWT validation (fallback / dev mode) ──────────
        String token = extractBearerToken(request);
        if (StringUtils.hasText(token)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(signingKey)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                String email = claims.getSubject();
                @SuppressWarnings("unchecked")
                List<String> roles = (List<String>) claims.get("roles", List.class);

                if (email != null && roles != null) {
                    List<SimpleGrantedAuthority> authorities = roles.stream()
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toList());

                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(email, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);

                    log.debug("Authenticated via direct JWT: user={}", email);
                }
            } catch (JwtException e) {
                log.debug("JWT validation failed in crew-service: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
