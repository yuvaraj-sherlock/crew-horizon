# ✈️ CREW Horizon — Airlines Crew Management System

> **Production-Ready Enterprise Microservices Platform**
> Built with Spring Boot 3 · Spring Cloud Kubernetes · JWT + RBAC · Redis · PostgreSQL · Docker · Kubernetes

---

## 📋 Table of Contents

- [Architecture Overview](#architecture-overview)
- [Services](#services)
- [Technology Stack](#technology-stack)
- [Security Design](#security-design)
- [Quick Start (Local)](#quick-start-local)
- [Kubernetes Deployment](#kubernetes-deployment)
- [CI/CD Pipeline](#cicd-pipeline)
- [API Documentation](#api-documentation)
- [FTL Compliance Engine](#ftl-compliance-engine)
- [Project Structure](#project-structure)

---

## 🏗️ Architecture Overview

```
                         ┌──────────────────────────────────────────┐
  External Clients       │          CREW Horizon Platform           │
  (Web / Mobile / API)   │                                          │
         │               │  ┌──────────────────────────────────┐   │
         ▼               │  │         API Gateway :8080        │   │
  ┌─────────────┐        │  │  JWT Validation · Rate Limiting  │   │
  │  Kubernetes │───────►│  │  Request Routing · Logging · AOP │   │
  │   Ingress   │        │  └──┬────────┬───────┬──────┬───────┘   │
  └─────────────┘        │     │        │       │      │           │
                         │     ▼        ▼       ▼      ▼           │
                         │  ┌──────┐ ┌──────┐ ┌──────┐ ┌───────┐  │
                         │  │Auth  │ │Crew  │ │Flight│ │Roster │  │
                         │  │:8081 │ │:8082 │ │:8083 │ │:8084  │  │
                         │  └──────┘ └──────┘ └──────┘ └───────┘  │
                         │                                ┌───────┐ │
                         │                                │Notif. │ │
                         │                                │:8085  │ │
                         │                                └───────┘ │
                         │         ┌──────────┐  ┌───────────┐     │
                         │         │PostgreSQL│  │   Redis   │     │
                         │         │ (5 DBs)  │  │(Cache/BL) │     │
                         │         └──────────┘  └───────────┘     │
                         └──────────────────────────────────────────┘
```

### Why This Architecture?

| Pattern | Why Used |
|---|---|
| **API Gateway** | Single entry point — centralized JWT auth, rate limiting, routing |
| **Spring Cloud Kubernetes** | Native K8s discovery without Eureka overhead |
| **Bounded Contexts** | Each service owns its domain data exclusively |
| **Redis Caching** | Sub-millisecond crew profile lookups during scheduling |
| **Circuit Breaker** | Prevents cascading failures across service dependencies |
| **FTL Engine** | Legal ICAO duty time compliance enforced at assignment time |
| **Flyway Migrations** | Version-controlled, auditable schema changes |

---

## 🔧 Services

| Service | Port | Responsibility |
|---|---|---|
| **api-gateway** | 8080 | JWT validation, routing, rate limiting, logging |
| **auth-service** | 8081 | User authentication, JWT issuance, RBAC, token blacklist |
| **crew-service** | 8082 | Crew profiles, qualifications, duty status |
| **flight-service** | 8083 | Flight schedules, aircraft assignments, status tracking |
| **roster-service** | 8084 | Crew-flight assignments, FTL compliance, scheduling |
| **notification-service** | 8085 | Async email/push notifications with retry logic |

---

## 🛠️ Technology Stack

```
Language:     Java 17
Framework:    Spring Boot 3.2.4
Security:     Spring Security 6 · JWT (JJWT 0.12.5) · RBAC
Persistence:  Spring Data JPA · Hibernate · PostgreSQL 16
Caching:      Spring Cache · Redis 7.2
Mapping:      MapStruct 1.5.5 (compile-time, zero-reflection)
Resilience:   Resilience4j 2.2 (Circuit Breaker, Retry, Rate Limiter)
HTTP Client:  Spring WebClient (reactive, non-blocking)
Migrations:   Flyway
Docs:         SpringDoc OpenAPI 2.3
Container:    Docker (multi-stage, non-root, ~200MB images)
Orchestration:Kubernetes 1.28+ (EKS / GKE / AKS)
CI/CD:        GitHub Actions
Monitoring:   Micrometer + Prometheus + Grafana
```

---

## 🔐 Security Design

### Authentication Flow
```
Client → POST /api/v1/auth/login
       ← { accessToken (15min), refreshToken (7days) }

Client → GET /api/v1/crew/{id}
         Authorization: Bearer <accessToken>
       → API Gateway validates JWT (HMAC-SHA256)
       → Forwards X-Authenticated-User + X-User-Roles headers
       → crew-service trusts these headers (network policy enforced)
```

### RBAC Roles
| Role | Permissions |
|---|---|
| `ROLE_ADMIN` | Full system access |
| `ROLE_CREW_SCHEDULER` | Create/modify rosters and assignments |
| `ROLE_OPERATIONS` | Read-only operational data |
| `ROLE_PILOT` | Own roster + flight info (read-only) |
| `ROLE_CABIN_CREW` | Own roster + basic flight info |
| `ROLE_HR_MANAGER` | Crew profile management |
| `ROLE_COMPLIANCE` | Read-only + audit reports |

### Token Revocation
JWT tokens are stateless but can be revoked via Redis blacklist:
- **Logout**: Token added to blacklist until natural expiry
- **Forced revocation**: Admin can blacklist any token by JTI
- **Blacklist TTL**: Matches token remaining validity — zero cleanup overhead

---

## 🚀 Quick Start (Local)

### Prerequisites
- Docker Desktop 4.x+
- Docker Compose 2.x+
- Java 17 (for local Maven builds)

### Start Full Stack
```bash
# Clone repository
git clone https://github.com/your-org/crew-horizon.git
cd crew-horizon

# Start infrastructure + all services
docker compose up -d

# Start with developer tools (pgAdmin, MailHog, RedisInsight)
docker compose --profile tools up -d

# View logs for a specific service
docker compose logs -f crew-service

# Check all service health
curl http://localhost:8080/actuator/health
```

### Service URLs (Local)
| Service | URL |
|---|---|
| API Gateway | http://localhost:8080 |
| Auth Service API Docs | http://localhost:8081/swagger-ui.html |
| Crew Service API Docs | http://localhost:8082/swagger-ui.html |
| pgAdmin | http://localhost:5050 |
| MailHog (email viewer) | http://localhost:8025 |
| RedisInsight | http://localhost:5540 |

### First API Call
```bash
# 1. Login (get JWT token)
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@crew-horizon.com","password":"Admin@CrewH0rizon!"}'

# 2. Use the access token
export TOKEN="<paste_access_token_here>"

# 3. Get crew members
curl http://localhost:8080/api/v1/crew \
  -H "Authorization: Bearer $TOKEN"

# 4. Create a crew member
curl -X POST http://localhost:8080/api/v1/crew \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "employeeId": "EK123456",
    "firstName": "Ahmed",
    "lastName": "Al-Rashidi",
    "email": "ahmed.alrashidi@crew-horizon.com",
    "crewType": "CAPTAIN",
    "baseAirport": "DXB",
    "dateOfHire": "2020-03-15"
  }'
```

---

## ☸️ Kubernetes Deployment

### Prerequisites
- Kubernetes cluster (EKS / GKE / AKS)
- kubectl configured
- cert-manager installed (for TLS)
- NGINX Ingress Controller installed

### Deploy to Kubernetes
```bash
# 1. Create namespace
kubectl apply -f k8s/namespace.yml

# 2. Create RBAC (required for Spring Cloud Kubernetes config)
kubectl apply -f k8s/rbac.yml

# 3. Create Secrets (update with real values first!)
# Edit k8s/secrets/crew-horizon-secrets.yml with real base64-encoded values
kubectl apply -f k8s/secrets/

# 4. Create ConfigMaps
kubectl apply -f k8s/configmaps/

# 5. Deploy infrastructure
kubectl apply -f k8s/infrastructure.yml

# 6. Wait for PostgreSQL and Redis to be ready
kubectl wait --for=condition=ready pod -l app=postgres -n crew-horizon --timeout=120s
kubectl wait --for=condition=ready pod -l app=redis -n crew-horizon --timeout=60s

# 7. Deploy all microservices
for svc in api-gateway auth-service crew-service flight-service roster-service notification-service; do
  kubectl apply -f k8s/$svc/deployment.yml
done

# 8. Watch rollout status
kubectl get pods -n crew-horizon -w
```

### Verify Deployment
```bash
# Check all pods are running
kubectl get pods -n crew-horizon

# Check service endpoints
kubectl get services -n crew-horizon

# View gateway logs
kubectl logs -l app=api-gateway -n crew-horizon --tail=50

# Test health endpoint
kubectl port-forward svc/api-gateway 8080:8080 -n crew-horizon &
curl http://localhost:8080/actuator/health
```

---

## 🔄 CI/CD Pipeline

The GitHub Actions pipeline runs on every push to `main` or `develop`:

```
Push to GitHub
     │
     ▼
detect-changes  ──→ Only build services with changed files
     │
     ▼
test (parallel) ──→ Unit tests + Integration tests + Coverage
     │
     ▼
security-scan   ──→ OWASP CVE scan + Trivy image scan
     │
     ▼
build-push      ──→ Multi-stage Docker build → GHCR registry
     │
     ▼
deploy-staging  ──→ Auto-deploy to staging K8s cluster
     │
     ▼
 ⏸ Manual Approval Gate (required reviewer)
     │
     ▼
deploy-prod     ──→ Rolling update to production + smoke test
     │
     ▼
notify-slack    ──→ Deployment success/failure notification
```

---

## 📖 API Documentation

All services expose OpenAPI (Swagger) documentation:

- **Auth Service**: `GET /v3/api-docs` | `/swagger-ui.html`
- **Crew Service**: `GET /v3/api-docs` | `/swagger-ui.html`
- **Flight Service**: `GET /v3/api-docs` | `/swagger-ui.html`
- **Roster Service**: `GET /v3/api-docs` | `/swagger-ui.html`
- **Notification Service**: `GET /v3/api-docs` | `/swagger-ui.html`

---

## ✈️ FTL Compliance Engine

The roster-service enforces **ICAO Annex 6 Flight and Duty Time Limitations**:

| Rule | Limit | Enforcement |
|---|---|---|
| Max Flight Duty Period (FDP) | 13 hours | Pre-assignment validation |
| Min rest between duties | 10 hours | Pre-assignment validation |
| Max flight time (28 days) | 100 hours | Rolling window check |
| Max duty time (7 days) | 60 hours | Rolling window check |

Violations return HTTP 422 with the specific rule violated. Schedulers with override authority can bypass with `overrideFtl: true` — all overrides are audit-logged.

---

## 📁 Project Structure

```
crew-horizon/
├── pom.xml                          # Parent POM (dependency management)
├── docker-compose.yml               # Local development stack
├── api-gateway/                     # Spring Cloud Gateway (port 8080)
│   ├── src/main/java/
│   │   └── com/crewhorizon/apigateway/
│   │       ├── filter/              # JWT + Logging global filters
│   │       ├── config/              # Route definitions, rate limiter
│   │       └── security/            # JWT token validator
│   └── Dockerfile
├── auth-service/                    # Authentication (port 8081)
│   ├── src/main/java/
│   │   └── com/crewhorizon/authservice/
│   │       ├── controller/          # AuthController
│   │       ├── service/impl/        # AuthServiceImpl
│   │       ├── security/            # JWT provider, filters, entry point
│   │       ├── entity/              # UserEntity, RoleEntity
│   │       ├── repository/          # UserRepository, RoleRepository
│   │       ├── dto/                 # LoginRequest, RegisterRequest, AuthResponse
│   │       └── exception/           # GlobalExceptionHandler
│   └── src/main/resources/
│       ├── application.yml
│       └── db/migration/V1__initial_schema.sql
├── crew-service/                    # Crew Management (port 8082)
│   ├── src/main/java/
│   │   └── com/crewhorizon/crewservice/
│   │       ├── aop/                 # AuditLoggingAspect
│   │       ├── config/              # RedisConfig, SecurityConfig
│   │       ├── controller/          # CrewMemberController
│   │       ├── entity/              # CrewMemberEntity, CrewQualificationEntity
│   │       ├── mapper/              # CrewMemberMapper (MapStruct)
│   │       ├── repository/          # CrewMemberRepository (JPA + Specification)
│   │       └── service/impl/        # CrewServiceImpl (cached)
│   └── src/main/resources/
│       └── db/migration/V1__initial_schema.sql
├── flight-service/                  # Flight Management (port 8083)
├── roster-service/                  # Crew Scheduling (port 8084)
│   ├── src/main/java/
│   │   └── com/crewhorizon/rosterservice/
│   │       ├── service/             # FtlComplianceService, InterServiceClient
│   │       └── service/impl/        # RosterServiceImpl (orchestrator)
├── notification-service/            # Async Notifications (port 8085)
└── k8s/                             # Kubernetes manifests
    ├── namespace.yml
    ├── rbac.yml
    ├── infrastructure.yml           # Redis, PostgreSQL, Ingress
    ├── configmaps/
    ├── secrets/
    └── {service}/deployment.yml     # Deployment + Service + HPA per service
```

---

## 🔧 Environment Variables

| Variable | Default | Description |
|---|---|---|
| `JWT_SECRET` | **required** | HMAC-SHA256 signing key (min 32 chars) |
| `DB_URL` | jdbc:postgresql://... | PostgreSQL JDBC URL |
| `DB_USERNAME` | crew_horizon_user | DB username |
| `DB_PASSWORD` | **required** | DB password (from K8s Secret) |
| `REDIS_HOST` | redis-service | Redis hostname |
| `REDIS_PORT` | 6379 | Redis port |
| `LOG_LEVEL` | INFO | Logging level (DEBUG in dev) |
| `ENVIRONMENT` | production | Environment name (affects log format) |
| `JPA_DDL_AUTO` | validate | Hibernate DDL mode (update in dev only) |

---

## 📊 Monitoring

Prometheus metrics are exposed at `/actuator/prometheus` on every service.

Key metrics to monitor:
- `http_server_requests_seconds` — API latency by endpoint
- `resilience4j_circuitbreaker_state` — Circuit breaker status
- `jvm_memory_used_bytes` — JVM heap usage
- `hikaricp_connections_active` — DB connection pool usage
- `cache_gets_total` — Redis cache hit/miss ratio

---

*CREW Horizon — Built for airline-grade reliability*
