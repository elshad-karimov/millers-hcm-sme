# M165 (PRD §18.2): Multi-stage Dockerfile for the Millers HCM Spring Boot backend.
#
# Stage 1 (builder): compiles and packages the JAR (used for local dev builds;
#                    CI uses pre-built JAR from the artifact store).
# Stage 2 (runtime): lean JRE image — only the exploded Spring Boot layers land here.
#
# Build:
#   docker build -t millers-hcm:latest .
#
# Run (mirrors docker-compose target, points at the local infra stack):
#   docker run --rm -p 8080:8080 \
#     -e SPRING_DATASOURCE_URL=jdbc:postgresql://hcm-postgres:5432/hcm \
#     millers-hcm:latest

# ── Stage 1: Maven build ────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Cache Maven dependencies before copying source to maximise layer reuse.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f pom.xml dependency:go-offline -B -q

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f pom.xml package -DskipTests -B -q

# ── Stage 2: Exploded JAR runtime ───────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user for security hardening (PRD §14)
RUN addgroup -S hcm && adduser -S hcm -G hcm
USER hcm

WORKDIR /app

# Spring Boot layered JAR: split into four cache-friendly layers.
ARG JAR_FILE=target/*.jar
COPY --from=builder /workspace/${JAR_FILE} app.jar

# Explode the layered JAR so Docker can cache dependencies separately from
# application classes, speeding up subsequent rebuilds significantly.
RUN java -Djarmode=layertools -jar app.jar extract

# Runtime layers (largest first for cache efficiency)
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S hcm && adduser -S hcm -G hcm
USER hcm
WORKDIR /app
COPY --from=runtime /app/dependencies/ ./
COPY --from=runtime /app/spring-boot-loader/ ./
COPY --from=runtime /app/snapshot-dependencies/ ./
COPY --from=runtime /app/application/ ./

EXPOSE 8080

# JVM tuning: container-aware memory limits, ZGC for low-latency GC pauses,
# Actuator readiness probe enabled via Spring Boot's liveness/readiness endpoints.
ENV JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational \
    -XX:MaxRAMPercentage=75.0 \
    -Djava.security.egd=file:/dev/./urandom \
    -Dserver.port=8080"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]

# Health check mirrors the Actuator liveness probe
HEALTHCHECK --interval=10s --timeout=5s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1
