# Multi-stage build: compile and test in the builder, copy only the fat jar to the runtime.
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /build
COPY pom.xml spotbugs-exclude.xml ./
# Download all dependencies before copying source (layer cache)
RUN mvn -q -B dependency:go-offline -Djacoco.skip=true
COPY src src
RUN mvn -q -B verify

# ── Runtime ──────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-noble AS runtime
# Run as non-root: UID 1000 is the first user on most Linux hosts
RUN userdel --remove ubuntu 2>/dev/null; useradd --create-home --uid 1000 --user-group app
WORKDIR /app
RUN chown app:app /app
USER app
COPY --from=builder --chown=app:app /build/target/shortener-service-1.0.0.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
