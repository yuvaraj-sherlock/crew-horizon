package com.crewhorizon.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ============================================================
 * CREW HORIZON - API Gateway Entry Point
 * ============================================================
 * WHAT: The main entry point for the API Gateway microservice.
 *       This service acts as the single ingress for all CREW
 *       Horizon client traffic.
 *
 * WHY:  The API Gateway pattern in microservices architecture
 *       solves the "cross-cutting concerns" problem:
 *       - Authentication/Authorization (JWT validation)
 *       - Rate limiting (protect downstream services)
 *       - Request logging (centralized audit trail)
 *       - API aggregation (reduce client round-trips)
 *       - SSL termination (handled here, not per-service)
 *
 *       By centralizing these concerns, individual microservices
 *       remain lean and focused on their business logic only.
 *
 * KUBERNETES NOTE: In Kubernetes, the API Gateway is backed by
 *       a Kubernetes Ingress resource + this Spring Cloud Gateway
 *       pod. The Ingress handles external load balancing while
 *       Spring Cloud Gateway handles intelligent routing and
 *       request transformation.
 * ============================================================
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
