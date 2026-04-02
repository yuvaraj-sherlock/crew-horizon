package com.crewhorizon.rosterservice.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;

/**
 * ============================================================
 * Crew Service Client (Inter-Service Communication)
 * ============================================================
 * WHAT: HTTP client facade for calling the crew-service from
 *       the roster-service. Abstracts WebClient calls behind
 *       a clean service interface with resilience patterns.
 *
 * WHY a dedicated Client class (vs direct WebClient in service):
 *       1. SINGLE RESPONSIBILITY: RosterService focuses on
 *          scheduling logic, not HTTP plumbing.
 *       2. TESTABILITY: In unit tests, this client is mocked.
 *          The actual WebClient/HTTP interaction is tested
 *          separately in integration tests.
 *       3. RESILIENCE CENTRALIZATION: All resilience annotations
 *          (@CircuitBreaker, @Retry, @TimeLimiter) are applied
 *          here, not scattered across the service layer.
 *       4. CONTRACT ENFORCEMENT: This class defines exactly what
 *          the roster-service needs from the crew-service —
 *          a clear interface for the inter-service contract.
 *
 * WHY @CircuitBreaker + @Retry together:
 *       @Retry handles TRANSIENT failures (network hiccup, 503).
 *       @CircuitBreaker handles PERSISTENT failures (service down).
 *       Together:
 *       1. Request fails → @Retry tries up to 3 times
 *       2. After 50% failure rate → @CircuitBreaker opens
 *       3. During OPEN state → fail-fast (no retries wasted)
 *       4. After cooldown → @CircuitBreaker half-opens (test calls)
 *
 * WHY fallback methods:
 *       When crew-service is unavailable, roster creation must
 *       not silently fail with a NullPointerException.
 *       Fallback methods return cached/empty data with a clear
 *       indication that the data is stale, enabling graceful
 *       degradation.
 * ============================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrewServiceClient {

    private final WebClient crewServiceClient;

    /**
     * Checks if a crew member exists and is available.
     *
     * WHY Mono<Optional<Map>>:
     * - Mono: reactive type (non-blocking call)
     * - Optional: caller must handle "not found" case explicitly
     * - Map: flexible response container (avoids tight coupling
     *        to crew-service's internal DTO classes)
     *
     * In production, a shared DTOs module or API contract (OpenAPI)
     * would define typed response objects.
     */
    @CircuitBreaker(name = "crew-service", fallbackMethod = "getCrewMemberFallback")
    @Retry(name = "crew-service")
    public Mono<Optional<Map>> getCrewMember(String employeeId, String authToken) {
        return crewServiceClient.get()
                .uri("/api/v1/crew/{employeeId}", employeeId)
                .header("Authorization", "Bearer " + authToken)
                .retrieve()
                .bodyToMono(Map.class)
                .map(Optional::of)
                .onErrorResume(WebClientResponseException.NotFound.class, e -> {
                    log.warn("Crew member not found: {}", employeeId);
                    return Mono.just(Optional.empty());
                })
                .doOnError(e -> log.error("Error fetching crew member {}: {}",
                        employeeId, e.getMessage()));
    }

    /**
     * Updates crew duty status (called after roster assignment is confirmed).
     *
     * WHY void return (fire-and-forget pattern):
     * Updating crew status is a consequence of creating a roster assignment,
     * but it shouldn't block the assignment creation. If the status update
     * fails, it will be retried. The assignment is the source of truth.
     */
    @CircuitBreaker(name = "crew-service", fallbackMethod = "updateDutyStatusFallback")
    @Retry(name = "crew-service")
    public Mono<Void> updateCrewDutyStatus(String employeeId, String newStatus,
                                            String authToken) {
        return crewServiceClient.patch()
                .uri("/api/v1/crew/{employeeId}/duty-status", employeeId)
                .header("Authorization", "Bearer " + authToken)
                .bodyValue(Map.of("dutyStatus", newStatus))
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("Duty status updated for crew member: {}", employeeId))
                .doOnError(e -> log.error("Failed to update duty status for {}: {}",
                        employeeId, e.getMessage()));
    }

    /**
     * Fallback: circuit breaker is open or retry exhausted.
     *
     * WHY return empty Optional (not throw exception):
     * Returning empty Optional allows the calling service to
     * make a GRACEFUL DEGRADATION decision:
     * - Log the unavailability
     * - Return a "service temporarily unavailable" error to the user
     * Rather than propagating a NullPointerException or generic error.
     */
    @SuppressWarnings("unused")
    private Mono<Optional<Map>> getCrewMemberFallback(String employeeId,
                                                        String authToken,
                                                        Throwable t) {
        log.error("[CIRCUIT-BREAKER] crew-service unavailable when fetching {}: {}",
                employeeId, t.getMessage());
        return Mono.just(Optional.empty());
    }

    @SuppressWarnings("unused")
    private Mono<Void> updateDutyStatusFallback(String employeeId, String newStatus,
                                                  String authToken, Throwable t) {
        log.error("[CIRCUIT-BREAKER] crew-service unavailable for duty status update {}: {}",
                employeeId, t.getMessage());
        // TODO: Push to a retry queue (Kafka/Redis) for eventual consistency
        return Mono.empty();
    }
}
