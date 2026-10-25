# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY pom.xml .
COPY shared shared
COPY services/scheduler-service services/scheduler-service
COPY mvnw .
COPY .mvn .mvn

RUN chmod +x mvnw && \
    ./mvnw -q -pl services/scheduler-service -am -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd --system --create-home appuser
COPY --from=build /workspace/services/scheduler-service/target/*.jar app.jar
USER appuser
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
