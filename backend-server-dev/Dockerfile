# Multi-stage Dockerfile for Spring Boot with Gradle
# Stage 1: Build (JDK required for compilation)
FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /build

# Copy Gradle wrapper and build files first (for caching)
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradle.properties .

# Make gradlew executable
RUN chmod +x gradlew

# Download dependencies (cached layer - only re-runs if build files change)
RUN ./gradlew dependencies --no-daemon || true

# Copy source code
COPY src src

# Build the application and extract layers for better caching
RUN ./gradlew bootJar --no-daemon -x test && \
    java -Djarmode=layertools -jar build/libs/*.jar extract --destination build/extracted

# Stage 2: Runtime (JRE only - minimal image)
FROM eclipse-temurin:25-jre-alpine AS production

WORKDIR /app

# Create non-root user for security
RUN addgroup -g 1001 spring && \
    adduser -D -u 1001 -G spring spring && \
    chown -R spring:spring /app && \
    mkdir -p /app/logs && \
    chown -R spring:spring /app/logs

# Install curl for health checks
RUN apk add --no-cache curl

# Copy extracted layers from builder (better Docker layer caching)
COPY --from=builder --chown=spring:spring /build/build/extracted/dependencies/ ./
COPY --from=builder --chown=spring:spring /build/build/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=spring:spring /build/build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=spring:spring /build/build/extracted/application/ ./

# Switch to non-root user
USER spring

# Expose port
EXPOSE 8080

# Set JVM options for containerized environment
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health/liveness || exit 1

# Start using Spring Boot's layered launcher
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
