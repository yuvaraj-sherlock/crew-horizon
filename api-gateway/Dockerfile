# ============================================================
# CREW HORIZON - Multi-stage Dockerfile (Shared Pattern)
# ============================================================
# WHAT: Production-optimized Docker image build for a
#       Spring Boot microservice using multi-stage builds.
#
# WHY Multi-stage builds:
#       Stage 1 (builder): Contains Maven, JDK, source code.
#                          Large image (~800MB).
#       Stage 2 (runtime): Contains JRE only + the built JAR.
#                          Small image (~200MB).
#
#       The final image does NOT contain:
#       - Maven (not needed at runtime)
#       - Source code (security: no source in production image)
#       - Test dependencies
#       - Build tools
#
#       Result: ~75% smaller image = faster pulls, less attack surface.
#
# WHY Eclipse Temurin (formerly AdoptOpenJDK):
#       - Freely available (no Oracle license concerns)
#       - Production-quality, enterprise-tested JDK
#       - Regular security patches
#       - Available for all architectures (amd64, arm64 for M1 Macs)
#
# WHY Alpine base for runtime:
#       Alpine Linux is ~5MB vs Ubuntu's ~80MB.
#       Smaller base = smaller attack surface = faster pull times.
#       For Java, use eclipse-temurin-alpine which properly
#       handles Alpine's musl libc.
# ============================================================

# ─── STAGE 1: BUILD ─────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /workspace/app

# WHY copy pom.xml BEFORE source code:
# Docker layer caching. The pom.xml changes rarely; source code
# changes frequently. By copying pom.xml first and running
# dependency download in a separate layer, subsequent builds
# skip the slow dependency download if only source changed.
COPY pom.xml .
COPY ../pom.xml ../pom.xml

# Download dependencies (cached layer — only re-runs if pom.xml changes)
RUN mvn dependency:go-offline -B --no-transfer-progress 2>/dev/null || true

# Copy source code (this layer invalidates on every source change)
COPY src ./src

# Build the application
# WHY -DskipTests: Tests run in CI pipeline (see GitHub Actions workflow).
# Skipping here keeps Docker build fast and focused.
# The CI pipeline runs tests BEFORE building the Docker image.
RUN mvn package -DskipTests -B --no-transfer-progress

# WHY layertools:
# Spring Boot 2.3+ supports layered JARs. Layertools extracts
# the JAR into layers (dependencies, spring-boot-loader, snapshot-dependencies, application).
# Docker caches unchanged layers (e.g., dependencies don't change between builds).
# Only the 'application' layer (your code) changes on most builds.
# This makes subsequent Docker builds and Kubernetes rolling updates MUCH faster.
RUN java -Djarmode=layertools -jar target/*.jar extract

# ─── STAGE 2: RUNTIME ───────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

# WHY create non-root user:
# NEVER run containers as root. If a container is compromised,
# an attacker with root access could escape to the host.
# A non-root user limits the blast radius of any breach.
RUN addgroup -S springapp && adduser -S springapp -G springapp

WORKDIR /app

# Copy extracted layers from builder stage
# WHY copy in dependency order (rarely changed → often changed):
# Docker processes COPY commands sequentially. Putting rarely
# changed layers first maximizes cache hit rate.
COPY --from=builder --chown=springapp:springapp /workspace/app/dependencies/ ./
COPY --from=builder --chown=springapp:springapp /workspace/app/spring-boot-loader/ ./
COPY --from=builder --chown=springapp:springapp /workspace/app/snapshot-dependencies/ ./
COPY --from=builder --chown=springapp:springapp /workspace/app/application/ ./

# Switch to non-root user
USER springapp

# WHY EXPOSE (documentation only):
# EXPOSE doesn't actually publish the port — it's documentation.
# The actual port binding happens in K8s Service definition.
EXPOSE 8080

# WHY JVM flags here (not in CMD):
# JAVA_TOOL_OPTIONS is automatically picked up by the JVM.
# It can be OVERRIDDEN by K8s env vars (more flexible than CMD args).
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# WHY JarLauncher (not the executable JAR directly):
# JarLauncher is the Spring Boot launcher that understands the
# layered JAR structure. It loads layers in the correct order.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
