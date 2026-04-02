package com.crewhorizon.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * ============================================================
 * Request/Response Logging Filter
 * ============================================================
 * WHAT: Logs every request entering the gateway and its
 *       corresponding response. Records method, path, status,
 *       user identity, and request duration.
 *
 * WHY Centralized Gateway Logging:
 *       1. AUDIT TRAIL: Aviation industry regulations (IATA, FAA)
 *          require comprehensive audit logs for crew management
 *          operations. Every API call must be traceable.
 *
 *       2. DISTRIBUTED TRACING: The X-Request-Id header injected
 *          here propagates through ALL downstream services,
 *          enabling correlation of logs across the entire
 *          request lifecycle (integrates with Jaeger/Zipkin).
 *
 *       3. SINGLE LOGGING POINT: Without gateway logging, each
 *          microservice logs independently with inconsistent
 *          formats. Centralizing here enables uniform log
 *          aggregation (ELK Stack / Grafana Loki).
 *
 *       4. PERFORMANCE MONITORING: Response time logging enables
 *          SLA monitoring and identification of slow services
 *          before they cascade into failures.
 *
 * WHY Order(2) after JwtAuthenticationFilter(HIGHEST_PRECEDENCE):
 *       Logging runs AFTER auth so we can include the authenticated
 *       username in the log entry. Auth failures are logged by
 *       the JwtAuthenticationFilter itself.
 * ============================================================
 */
@Slf4j
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        long startTime = System.currentTimeMillis();

        // Extract correlation ID injected by JwtAuthenticationFilter
        String requestId = request.getHeaders().getFirst("X-Request-Id");
        if (requestId == null) {
            requestId = java.util.UUID.randomUUID().toString();
        }

        String authenticatedUser = request.getHeaders().getFirst("X-Authenticated-User");
        String method = request.getMethod().name();
        String path = request.getURI().getPath();
        String clientIp = getClientIp(request);

        final String finalRequestId = requestId;

        log.info("[GATEWAY-IN] requestId={} | method={} | path={} | user={} | ip={}",
                finalRequestId, method, path, authenticatedUser, clientIp);

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            ServerHttpResponse response = exchange.getResponse();
            long duration = System.currentTimeMillis() - startTime;
            int statusCode = response.getStatusCode() != null
                    ? response.getStatusCode().value() : 0;

            /*
             * WHY structured log format (key=value):
             * Structured logs are machine-parseable. Log aggregators
             * (Logstash, Fluentd) can index these fields directly,
             * enabling powerful filtering in Kibana/Grafana dashboards
             * e.g., "show all requests > 2000ms in the last hour".
             */
            log.info("[GATEWAY-OUT] requestId={} | method={} | path={} | status={} | duration={}ms | user={}",
                    finalRequestId, method, path, statusCode, duration, authenticatedUser);

            // WHY warn on slow requests:
            // Proactive alerting on performance degradation before users
            // notice. Threshold of 2s is a common API SLA boundary.
            if (duration > 2000) {
                log.warn("[SLOW-REQUEST] requestId={} | path={} | duration={}ms exceeded 2000ms threshold",
                        finalRequestId, path, duration);
            }
        }));
    }

    /**
     * WHY extract client IP from X-Forwarded-For:
     * In Kubernetes, requests pass through multiple proxies (Ingress,
     * LoadBalancer). The actual client IP is in X-Forwarded-For header,
     * not in the remote address (which would be the last proxy's IP).
     */
    private String getClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }

    @Override
    public int getOrder() {
        // Run after JWT auth filter (HIGHEST_PRECEDENCE = Integer.MIN_VALUE)
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
