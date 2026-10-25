# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY pom.xml .
COPY shared shared
COPY services/ratelimit-service services/ratelimit-service
COPY mvnw .
COPY .mvn .mvn

RUN chmod +x mvnw && \
    ./mvnw -q -pl services/ratelimit-service -am -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd --system --create-home appuser
COPY --from=build /workspace/services/ratelimit-service/target/*.jar app.jar
USER appuser
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
