package com.crewhorizon.apigateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * ============================================================
 * Gateway Route Configuration
 * ============================================================
 * WHAT: Defines routing rules that map incoming API paths to
 *       backend microservices. Also configures per-route
 *       rate limiting, circuit breaking, and request rewriting.
 *
 * WHY Code-Based Routes vs YAML Routes:
 *       Both approaches work in Spring Cloud Gateway.
 *       Code-based routes are used here for complex routes that
 *       benefit from type safety and IDE support.
 *       Simple routes are in application.yml for readability.
 *
 * WHY Kubernetes Service Names as URIs:
 *       In Kubernetes, services are accessible via their DNS name:
 *       http://{service-name}.{namespace}.svc.cluster.local
 *       Using lb:// prefix enables Spring Cloud LoadBalancer
 *       to resolve to Kubernetes endpoints, providing:
 *       - Native K8s service discovery (no Eureka needed)
 *       - Automatic failover when pods are unhealthy
 *       - Rolling update compatibility
 *
 * WHY Rate Limiting at the Gateway:
 *       Protects ALL downstream services from DDoS and traffic
 *       spikes with a single configuration point. Each user
 *       (identified by X-Authenticated-User header) gets their
 *       own rate limit bucket (Token Bucket algorithm via Redis).
 * ============================================================
 */
@Configuration
public class GatewayRouteConfig {

    @Bean
    public RouteLocator crewHorizonRoutes(RouteLocatorBuilder builder) {
        return builder.routes()

                /*
                 * AUTH SERVICE ROUTE
                 * WHY no rate limiter on auth routes in this route def:
                 * Auth routes are public (no JWT required). They have
                 * their own brute-force protection within the auth-service
                 * (account lockout after N failed attempts).
                 */
                .route("auth-service", r -> r
                        .path("/api/v1/auth/**")
                        .filters(f -> f
                                .rewritePath("/api/v1/auth/(?<segment>.*)",
                                        "/api/v1/auth/${segment}")
                                .addRequestHeader("X-Gateway-Source", "crew-horizon-gateway")
                                .circuitBreaker(config -> config
                                        .setName("auth-service-cb")
                                        .setFallbackUri("forward:/fallback/auth")))
                        .uri("lb://auth-service"))

                /*
                 * CREW SERVICE ROUTE
                 * WHY rewritePath filter:
                 * Decouples external API path structure from internal
                 * service paths. External consumers use versioned paths
                 * (/api/v1/crew) while internal services can evolve
                 * their paths independently.
                 */
                .route("crew-service", r -> r
                        .path("/api/v1/crew/**")
                        .filters(f -> f
                                .rewritePath("/api/v1/crew/(?<segment>.*)",
                                        "/api/v1/crew/${segment}")
                                .requestRateLimiter(config -> config
                                        .setRateLimiter(redisRateLimiter())
                                        .setKeyResolver(userKeyResolver()))
                                .circuitBreaker(config -> config
                                        .setName("crew-service-cb")
                                        .setFallbackUri("forward:/fallback/crew"))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(3)
                                        .setBackoff(Duration.ofMillis(100),
                                                Duration.ofSeconds(2), 2, true)))
                        .uri("lb://crew-service"))

                /*
                 * FLIGHT SERVICE ROUTE
                 */
                .route("flight-service", r -> r
                        .path("/api/v1/flights/**")
                        .filters(f -> f
                                .rewritePath("/api/v1/flights/(?<segment>.*)",
                                        "/api/v1/flights/${segment}")
                                .requestRateLimiter(config -> config
                                        .setRateLimiter(redisRateLimiter())
                                        .setKeyResolver(userKeyResolver()))
                                .circuitBreaker(config -> config
                                        .setName("flight-service-cb")
                                        .setFallbackUri("forward:/fallback/flight")))
                        .uri("lb://flight-service"))

                /*
                 * ROSTER SERVICE ROUTE
                 */
                .route("roster-service", r -> r
                        .path("/api/v1/rosters/**")
                        .filters(f -> f
                                .rewritePath("/api/v1/rosters/(?<segment>.*)",
                                        "/api/v1/rosters/${segment}")
                                .requestRateLimiter(config -> config
                                        .setRateLimiter(redisRateLimiter())
                                        .setKeyResolver(userKeyResolver()))
                                .circuitBreaker(config -> config
                                        .setName("roster-service-cb")
                                        .setFallbackUri("forward:/fallback/roster")))
                        .uri("lb://roster-service"))

                /*
                 * NOTIFICATION SERVICE ROUTE
                 */
                .route("notification-service", r -> r
                        .path("/api/v1/notifications/**")
                        .filters(f -> f
                                .rewritePath("/api/v1/notifications/(?<segment>.*)",
                                        "/api/v1/notifications/${segment}")
                                .circuitBreaker(config -> config
                                        .setName("notification-service-cb")
                                        .setFallbackUri("forward:/fallback/notification")))
                        .uri("lb://notification-service"))

                .build();
    }

    /**
     * WHY Redis Rate Limiter (not in-memory):
     * In-memory rate limiting only works for a SINGLE instance.
     * With multiple gateway replicas in Kubernetes (for HA),
     * each pod would have its own counter — effectively multiplying
     * the limit by the number of pods.
     *
     * Redis provides a SHARED distributed counter so all gateway
     * replicas enforce the same limit collectively.
     *
     * replenishRate: tokens added per second (sustained traffic)
     * burstCapacity: max tokens in bucket (burst allowance)
     * requestedTokens: tokens consumed per request
     */
    @Bean
    public org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter redisRateLimiter() {
        return new org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter(
                50,   // replenishRate: 50 requests/second sustained
                100,  // burstCapacity: allow bursts up to 100 requests
                1     // requestedTokens: 1 token per request
        );
    }

    /**
     * WHY user-based key resolver (not IP-based):
     * Rate limiting by IP can be unfair for shared networks
     * (e.g., entire airline office on one IP). Limiting by
     * authenticated user identity is more precise and fair.
     *
     * The X-Authenticated-User header is set by JwtAuthenticationFilter,
     * so by this point it's guaranteed to be present and validated.
     */
    @Bean
    public org.springframework.cloud.gateway.filter.ratelimit.KeyResolver userKeyResolver() {
        return exchange -> reactor.core.publisher.Mono.justOrEmpty(
                exchange.getRequest().getHeaders().getFirst("X-Authenticated-User"))
                .defaultIfEmpty("anonymous");
    }
}
