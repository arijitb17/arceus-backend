# syntax=docker/dockerfile:1

# ---- Build stage: compiles this service against the reactor (shared libs + this module) ----
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# Copy the whole reactor so Maven can resolve the shared/* modules this
# service depends on (arceus-events, arceus-ratelimit, arceus-grpc-contracts).
COPY pom.xml .
COPY shared shared
COPY services/auth-service services/auth-service
COPY mvnw .
COPY .mvn .mvn

RUN chmod +x mvnw && \
    ./mvnw -q -pl services/auth-service -am -DskipTests package

# ---- Runtime stage: just the jar, on a slim JRE ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd --system --create-home appuser
COPY --from=build /workspace/services/auth-service/target/*.jar app.jar
USER appuser
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
