package com.crewhorizon.apigateway.filter;

import com.crewhorizon.apigateway.security.JwtTokenValidator;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * ============================================================
 * JWT Authentication Filter (Global Gateway Filter)
 * ============================================================
 * WHAT: A GlobalFilter that intercepts EVERY request through
 *       the gateway, validates the JWT token, and either:
 *       a) Forwards the request with enriched headers (username,
 *          roles) to the downstream service, OR
 *       b) Rejects the request with HTTP 401 Unauthorized.
 *
 * WHY GlobalFilter vs GatewayFilter:
 *       GlobalFilter applies to ALL routes automatically.
 *       GatewayFilter is route-specific.
 *       Using GlobalFilter ensures no route accidentally bypasses
 *       authentication — defense in depth.
 *
 * WHY Ordered with HIGHEST_PRECEDENCE:
 *       Authentication MUST run BEFORE routing, rate limiting,
 *       and other filters. If auth fails, we reject immediately
 *       without wasting resources on routing logic.
 *
 * WHY Reactive (Mono<Void>):
 *       Spring Cloud Gateway is built on WebFlux (Project Reactor).
 *       All filters MUST be reactive to avoid blocking the event
 *       loop. Blocking I/O in a reactive pipeline causes thread
 *       starvation and kills performance.
 * ============================================================
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter {

    private final JwtTokenValidator jwtTokenValidator;

    /**
     * WHY a whitelist of public paths:
     * Some endpoints (auth/login, health checks, API docs) must
     * be accessible without a token. Whitelisting explicitly is
     * safer than blacklisting — any new route defaults to PROTECTED.
     */
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/actuator/health",
            "/actuator/info",
            "/v3/api-docs",
            "/swagger-ui"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // WHY early return for public paths:
        // Short-circuit the filter chain for whitelisted paths.
        // Avoids JWT parsing overhead for endpoints that don't need auth.
        if (isPublicPath(path)) {
            log.debug("Bypassing JWT filter for public path: {}", path);
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        // WHY check Bearer prefix:
        // RFC 6750 defines Bearer Token usage in HTTP Authorization header.
        // "Bearer " prefix is the standard — non-compliant requests are rejected.
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or malformed Authorization header for path: {}", path);
            return unauthorizedResponse(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7); // Remove "Bearer " prefix

        try {
            Claims claims = jwtTokenValidator.validateAndExtractClaims(token);
            String username = jwtTokenValidator.extractUsername(claims);
            List<String> roles = jwtTokenValidator.extractRoles(claims);

            log.info("Authenticated request | user={} | roles={} | path={}", username, roles, path);

            /*
             * WHY mutate headers and forward:
             * Downstream microservices need to know WHO is making the request
             * and WHAT permissions they have. Instead of each service re-parsing
             * the JWT, the gateway extracts this info and forwards it as
             * trusted headers. Downstream services only accept these headers
             * from the gateway (enforced via network policy in Kubernetes).
             */
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-Authenticated-User", username)
                    .header("X-User-Roles", String.join(",", roles))
                    .header("X-Request-Id", java.util.UUID.randomUUID().toString())
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (JwtException e) {
            log.warn("JWT validation failed for path {}: {}", path, e.getMessage());
            return unauthorizedResponse(exchange, "Invalid or expired JWT token");
        }
    }

    /**
     * WHY a dedicated unauthorized response method:
     * Consistent error responses across ALL authentication failures.
     * The response is JSON-formatted to match the global error
     * response structure expected by API consumers.
     */
    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String errorBody = String.format(
                "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                message, java.time.Instant.now()
        );

        org.springframework.core.io.buffer.DataBuffer buffer = response.bufferFactory()
                .wrap(errorBody.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }
}
