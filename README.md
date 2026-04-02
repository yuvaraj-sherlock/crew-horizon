<<<<<<< HEAD
# ✈️ CREW Horizon — Airlines Crew Management System

> **Production-ready, cloud-native microservices platform for airline crew scheduling, management, and compliance.**

---

## 📐 Architecture Overview

```
                         ┌───────────────────────────────────────────┐
                         │            KUBERNETES CLUSTER              │
                         │                                           │
  Client                 │   ┌─────────┐    ┌────────────────────┐  │
  (Web/Mobile) ────────► │   │ Ingress │───►│    API Gateway     │  │
                         │   │ (NGINX) │    │  - JWT Validation  │  │
                         │   └─────────┘    │  - Rate Limiting   │  │
                         │                  │  - Request Routing │  │
                         │                  │  - Circuit Breaker │  │
                         │                  └─────────┬──────────┘  │
                         │                            │             │
                         │           ┌────────────────┼──────────┐  │
                         │           │                │          │  │
                         │    ┌──────▼──┐    ┌────────▼──┐  ┌───▼────────┐
                         │    │  Auth   │    │   Crew    │  │  Flight    │
                         │    │ Service │    │  Service  │  │  Service   │
                         │    └────┬────┘    └─────┬─────┘  └─────┬──────┘
                         │         │               │              │
                         │    ┌────▼──────────────►▼──────────────▼────┐
                         │    │            Roster Service               │
                         │    │    (Orchestrates crew + flight data)    │
                         │    └────────────────────┬───────────────────┘
                         │                          │
                         │    ┌─────────────────────▼──────────┐
                         │    │       Notification Service      │
                         │    │  (Email, SMS, Push Alerts)     │
                         │    └────────────────────────────────┘
                         │                                           │
                         │   ┌──────────┐  ┌───────┐              │
                         │   │PostgreSQL│  │ Redis │              │
                         │   │(per-svc) │  │Cache  │              │
                         │   └──────────┘  └───────┘              │
                         └───────────────────────────────────────────┘
```

---

## 🛠️ Technology Stack

| Layer | Technology | Why |
|-------|-----------|-----|
| Language | Java 17 (LTS) | LTS release with modern features (records, sealed classes) |
| Framework | Spring Boot 3.2 | Production-grade, cloud-native Java framework |
| Security | Spring Security + JWT | Stateless, scalable auth for microservices |
| Data | Spring Data JPA + PostgreSQL | ACID compliance for crew/flight data |
| Cache | Redis | Distributed cache for crew profiles and rate limiting |
| Service Discovery | Spring Cloud Kubernetes | Native K8s discovery (no Eureka) |
| Configuration | K8s ConfigMaps | 12-Factor config management |
| API Gateway | Spring Cloud Gateway | Reactive, non-blocking gateway |
| Resilience | Resilience4j | Circuit breaker, retry, rate limiter |
| Observability | Micrometer + Prometheus + Grafana | Full metrics observability |
| CI/CD | GitHub Actions | GitOps-native pipeline |
| Containerization | Docker (multi-stage) | Optimized, secure images |
| Orchestration | Kubernetes | Production-grade container orchestration |
| API Docs | SpringDoc OpenAPI 3 | Auto-generated, always up-to-date docs |
| Mapping | MapStruct | Compile-time DTO mapping |

---

## 🏗️ Microservices

| Service | Port | Responsibility |
|---------|------|----------------|
| **api-gateway** | 8080 | JWT validation, routing, rate limiting, circuit breaking |
| **auth-service** | 8081 | Authentication, JWT issuance, RBAC, user management |
| **crew-service** | 8082 | Crew profiles, qualifications, duty status |
| **flight-service** | 8083 | Flight schedules, aircraft assignments |
| **roster-service** | 8084 | Crew-flight assignments, FTL compliance |
| **notification-service** | 8085 | Email/SMS/push notifications |

---

## 🔐 Security Architecture

### JWT + Spring Security + RBAC

```
Login Request → Auth Service → BCrypt verification → JWT issuance
JWT Token     → API Gateway  → HMAC-SHA256 validation → Forward with X-Authenticated-User header
Protected API  → Downstream  → @PreAuthorize checks → Business logic
```

### Roles

| Role | Description |
|------|-------------|
| `ROLE_ADMIN` | Full system access |
| `ROLE_CREW_SCHEDULER` | Create/modify rosters |
| `ROLE_OPERATIONS` | Read-only operational view |
| `ROLE_PILOT` | Own roster + flight info |
| `ROLE_CABIN_CREW` | Own roster + basic info |
| `ROLE_HR_MANAGER` | Crew profile management |
| `ROLE_COMPLIANCE` | Audit report generation |

---

## 🚀 Quick Start (Local Development)

### Prerequisites
- Docker Desktop 4.0+
- Java 17+ (for running outside Docker)
- Maven 3.9+

### Start Everything
```bash
# Clone the repository
git clone https://github.com/your-org/crew-horizon.git
cd crew-horizon

# Start all services (first run downloads images ~5 min)
docker compose up -d

# Verify all services are healthy
docker compose ps
```

### Access Points
| Service | URL |
|---------|-----|
| API Gateway | http://localhost:8080 |
| Auth Service Swagger | http://localhost:8081/swagger-ui.html |
| Crew Service Swagger | http://localhost:8082/swagger-ui.html |
| Grafana | http://localhost:3001 (admin/admin) |
| Prometheus | http://localhost:9090 |

### First API Call
```bash
# 1. Register a test user
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "employeeId": "EK123456",
    "email": "test@crew-horizon.com",
    "firstName": "John",
    "lastName": "Doe",
    "password": "SecurePass@123",
    "confirmPassword": "SecurePass@123"
  }'

# 2. Login (get JWT)
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@crew-horizon.com","password":"SecurePass@123"}' \
  | jq -r '.accessToken')

# 3. Access protected endpoint
curl http://localhost:8080/api/v1/crew \
  -H "Authorization: Bearer $TOKEN"
```

---

## 📁 Project Structure

```
crew-horizon/
├── pom.xml                          # Parent POM (dependency management)
├── docker-compose.yml               # Local development environment
├── .github/
│   └── workflows/
│       └── ci-cd.yml                # CI/CD pipeline
│
├── api-gateway/                     # 🌐 Edge gateway
│   ├── src/main/java/.../
│   │   ├── filter/                  # JWT, logging, rate-limit filters
│   │   ├── security/                # JWT validator
│   │   ├── config/                  # Route configuration
│   │   └── controller/              # Fallback endpoints
│   └── src/main/resources/
│       └── application.yml
│
├── auth-service/                    # 🔐 Identity & Access Management
│   ├── src/main/java/.../
│   │   ├── controller/              # AuthController
│   │   ├── service/impl/            # AuthServiceImpl
│   │   ├── security/                # JWT provider, filters, entry point
│   │   ├── entity/                  # UserEntity, RoleEntity
│   │   ├── repository/              # UserRepository, RoleRepository
│   │   ├── dto/request|response/    # LoginRequest, AuthResponse, etc.
│   │   ├── exception/               # Custom exceptions + GlobalHandler
│   │   └── config/                  # SecurityConfig, RedisConfig
│   └── src/main/resources/
│       ├── application.yml
│       └── db/migration/            # Flyway SQL migrations
│
├── crew-service/                    # 👨‍✈️ Crew Member Management
│   ├── src/main/java/.../
│   │   ├── controller/              # CrewMemberController
│   │   ├── service/impl/            # CrewServiceImpl (with @Cacheable)
│   │   ├── aop/                     # AuditLoggingAspect
│   │   ├── entity/                  # CrewMemberEntity, CrewQualificationEntity
│   │   ├── repository/              # CrewMemberRepository
│   │   ├── dto/                     # CreateCrewMemberRequest, CrewMemberResponse
│   │   ├── mapper/                  # CrewMemberMapper (MapStruct)
│   │   └── exception/               # Exception classes + GlobalHandler
│
├── flight-service/                  # ✈️ Flight Schedule Management
├── roster-service/                  # 📋 Crew Scheduling & Rostering
├── notification-service/            # 📧 Alerts & Notifications
│
├── k8s/
│   ├── namespace-and-rbac.yaml      # K8s namespace, ServiceAccount, RBAC
│   ├── ingress.yaml                 # External access + network policies
│   ├── configmaps/                  # Per-service ConfigMaps
│   ├── secrets/                     # Secrets template (use ESO in prod)
│   ├── api-gateway/deployment.yaml  # Deployment + Service + HPA
│   └── crew-service/deployment.yaml # Deployment + Service + PDB + HPA
│
└── scripts/
    └── init-databases.sql           # DB initialization for local dev
```

---

## 🔄 CI/CD Pipeline

```
git push → GitHub Actions
    │
    ├── Code Quality (Checkstyle, SpotBugs)
    │
    ├── Test Matrix (6 services in parallel)
    │   ├── Unit Tests
    │   └── Integration Tests (Testcontainers)
    │
    ├── Security Scan
    │   ├── OWASP Dependency Check (CVE scanning)
    │   └── Trivy Docker Image Scan
    │
    ├── Build & Push Docker Images
    │   ├── Multi-architecture (amd64 + arm64)
    │   └── Push to GitHub Container Registry
    │
    ├── Deploy Staging (auto on main)
    │   └── Smoke Tests
    │
    └── Deploy Production (manual approval gate)
```

---

## 📊 Observability

### Metrics (Prometheus + Grafana)
- JVM heap, GC, thread metrics
- HTTP request rates, latencies, error rates per endpoint
- Circuit breaker state (CLOSED/OPEN/HALF_OPEN)
- Redis cache hit/miss rates
- Custom business metrics (active crew assignments, flight cancellations)

### Health Probes (Kubernetes)
```
GET /actuator/health/liveness   # Pod is alive (not deadlocked)
GET /actuator/health/readiness  # Pod is ready to serve traffic
```

### Structured Logging
All services emit JSON-formatted logs for ELK/Grafana Loki ingestion:
```json
{
  "timestamp": "2024-01-15T14:30:00Z",
  "level": "INFO",
  "service": "crew-service",
  "message": "[AUDIT-SUCCESS] CrewServiceImpl.createCrewMember()",
  "requestId": "abc-123"
}
```

---

## 🤝 Contributing

1. Create a feature branch: `git checkout -b feature/CH-123-add-ftl-validation`
2. Write tests for your changes
3. Run: `mvn verify` — all tests must pass
4. Open a Pull Request with description of changes
5. CI pipeline must be green before review

---

## 📄 License

Copyright © 2024 CREW Horizon. All rights reserved.
=======
# crew-horizon
crew management
>>>>>>> da9498b6563d05cf721844497e636541cb8d4ab9
