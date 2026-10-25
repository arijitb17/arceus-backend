# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY pom.xml .
COPY shared shared
COPY services/gateway-service services/gateway-service
COPY mvnw .
COPY .mvn .mvn

RUN chmod +x mvnw && \
    ./mvnw -q -pl services/gateway-service -am -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd --system --create-home appuser
COPY --from=build /workspace/services/gateway-service/target/*.jar app.jar
USER appuser
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
