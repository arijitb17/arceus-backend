# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY pom.xml .
COPY shared shared
COPY services/feature-flag-service services/feature-flag-service
COPY mvnw .
COPY .mvn .mvn

RUN chmod +x mvnw && \
    ./mvnw -q -pl services/feature-flag-service -am -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd --system --create-home appuser
COPY --from=build /workspace/services/feature-flag-service/target/*.jar app.jar
USER appuser
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
