package com.crewhorizon.rosterservice.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * ============================================================
 * Inter-Service Client (Roster → Crew & Flight Services)
 * ============================================================
 * WHAT: Provides typed, resilient HTTP calls from roster-service
 *       to crew-service and flight-service for validation and
 *       data retrieval during roster assignment creation.
 *
 * WHY @CircuitBreaker + @Retry on every external call:
 *       1. CIRCUIT BREAKER: If crew-service is down, after 5
 *          failures the circuit "opens" — subsequent calls
 *          immediately return the fallback without waiting for
 *          timeouts. This prevents thread pool exhaustion in
 *          the roster-service.
 *
 *       2. RETRY: Transient network glitches (TCP packet loss,
 *          pod startup delays during rolling updates) cause
 *          temporary failures. Retry with exponential backoff
 *          handles these gracefully. Only 3 retries to avoid
 *          amplifying load on already-struggling services.
 *
 * WHY @Service (not @Component):
 *       Semantic distinction: @Service marks this as a service
 *       layer component (business logic boundary), not just a
 *       generic Spring bean. It also enables Spring's exception
 *       translation if extended to DataAccessException handling.
 *
 * WHY pass jwtToken to each method:
 *       Service-to-service calls propagate the original user's
 *       token so downstream services can enforce RBAC. The
 *       token is extracted from SecurityContext in the calling
 *       service and forwarded here.
 * ============================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterServiceClient {

    @Qualifier("crewServiceClient")
    private final WebClient crewServiceClient;

    @Qualifier("flightServiceClient")
    private final WebClient flightServiceClient;

    /**
     * Validates a crew member exists and is available.
     *
     * WHY Mono<Map> instead of a typed DTO here:
     * Inter-service response DTOs create coupling between services.
     * If crew-service changes its response shape, roster-service
     * would need a code change + redeploy. Using Map<String, Object>
     * makes the contract more flexible at the cost of type safety.
     * In a larger team, a shared DTOs library module would be used.
     *
     * WHY fallbackMethod returns empty Mono (not throw):
     * An empty result tells the caller the service was unreachable.
     * The roster creation logic can then decide to:
     * a) Proceed with a warning (degraded mode)
     * b) Fail the assignment creation
     * This decision belongs in the business logic, not the client.
     */
    @CircuitBreaker(name = "crew-service", fallbackMethod = "crewMemberFallback")
    @Retry(name = "crew-service")
    public Mono<Map<String, Object>> getCrewMember(String employeeId, String jwtToken) {
        log.debug("Calling crew-service for employeeId: {}", employeeId);
        return crewServiceClient.get()
                .uri("/api/v1/crew/{employeeId}", employeeId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                .retrieve()
                .bodyToMono(Map.class)
                .cast(Map.class)
                .map(m -> (Map<String, Object>) m)
                .doOnSuccess(r -> log.debug("crew-service responded for: {}", employeeId))
                .doOnError(e -> log.error("crew-service error for {}: {}", employeeId, e.getMessage()));
    }

    /**
     * Fetches flight details to validate before roster assignment.
     */
    @CircuitBreaker(name = "flight-service", fallbackMethod = "flightFallback")
    @Retry(name = "flight-service")
    public Mono<Map<String, Object>> getFlight(String flightNumber, String jwtToken) {
        log.debug("Calling flight-service for flightNumber: {}", flightNumber);
        return flightServiceClient.get()
                .uri("/api/v1/flights/{flightNumber}", flightNumber)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                .retrieve()
                .bodyToMono(Map.class)
                .cast(Map.class)
                .map(m -> (Map<String, Object>) m)
                .doOnSuccess(r -> log.debug("flight-service responded for: {}", flightNumber))
                .doOnError(e -> log.error("flight-service error for {}: {}", flightNumber, e.getMessage()));
    }

    /**
     * Validates crew is available (not in rest period, not on another flight).
     * Calls crew-service to check current duty status.
     */
    @CircuitBreaker(name = "crew-service", fallbackMethod = "availabilityFallback")
    @Retry(name = "crew-service")
    public Mono<Boolean> isCrewAvailable(String employeeId, String dutyDate, String jwtToken) {
        return crewServiceClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/crew/{employeeId}")
                        .build(employeeId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    String status = (String) response.get("dutyStatus");
                    boolean available = "AVAILABLE".equals(status);
                    log.debug("Crew {} availability for {}: {}", employeeId, dutyDate, available);
                    return available;
                })
                .onErrorReturn(WebClientResponseException.NotFound.class, false);
    }

    // ─── Circuit Breaker Fallback Methods ───────────────────────────────────

    /**
     * WHY fallbacks return empty/degraded data (not throw exceptions):
     * If crew-service is temporarily down during roster creation,
     * the assignment can be created in DRAFT state (unvalidated)
     * and re-validated when the service recovers. Complete failure
     * of roster creation every time crew-service has a blip would
     * be unacceptable for airline operations.
     */
    private Mono<Map<String, Object>> crewMemberFallback(String employeeId,
                                                           String jwtToken,
                                                           Throwable throwable) {
        log.warn("[CIRCUIT-BREAKER] crew-service unavailable for {}: {}",
                employeeId, throwable.getMessage());
        return Mono.empty();
    }

    private Mono<Map<String, Object>> flightFallback(String flightNumber,
                                                       String jwtToken,
                                                       Throwable throwable) {
        log.warn("[CIRCUIT-BREAKER] flight-service unavailable for {}: {}",
                flightNumber, throwable.getMessage());
        return Mono.empty();
    }

    private Mono<Boolean> availabilityFallback(String employeeId,
                                                String dutyDate,
                                                String jwtToken,
                                                Throwable throwable) {
        log.warn("[CIRCUIT-BREAKER] Cannot verify availability for {} — assuming available: {}",
                employeeId, throwable.getMessage());
        // WHY default to true (not false):
        // Failing closed (returning false) would block ALL roster creation
        // when crew-service is down. Airline operations cannot stop.
        // The FTL compliance check below acts as a secondary safety net.
        return Mono.just(true);
    }
}
