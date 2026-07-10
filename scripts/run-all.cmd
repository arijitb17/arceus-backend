@echo off
REM Builds every module once, then opens each of the 7 services in its own
REM console window (so you can see each service's logs live and Ctrl+C
REM any one of them independently).
REM
REM Assumes Postgres, Redis, and RabbitMQ are already running and reachable
REM per your .env - this script does NOT start infrastructure, only the
REM Spring Boot services themselves.

cd /d "%~dp0\.."

echo ==> Building all modules (shared libs first, via reactor order)...
call mvnw.cmd -q clean install -DskipTests
if errorlevel 1 (
  echo Build failed - fix the error above before starting services.
  exit /b 1
)

start "auth-service"         cmd /k mvnw.cmd -pl services/auth-service spring-boot:run
start "notification-service" cmd /k mvnw.cmd -pl services/notification-service spring-boot:run
start "audit-service"        cmd /k mvnw.cmd -pl services/audit-service spring-boot:run
start "ratelimit-service"    cmd /k mvnw.cmd -pl services/ratelimit-service spring-boot:run
start "cache-service"        cmd /k mvnw.cmd -pl services/cache-service spring-boot:run
start "scheduler-service"    cmd /k mvnw.cmd -pl services/scheduler-service spring-boot:run
start "gateway-service"      cmd /k mvnw.cmd -pl services/gateway-service spring-boot:run

echo.
echo All 7 services are starting, each in its own window:
echo   gateway-service       http://localhost:8080/health
echo   auth-service          http://localhost:8081/swagger-ui.html
echo   notification-service  http://localhost:8082/swagger-ui.html
echo   audit-service         http://localhost:8083/swagger-ui.html
echo   ratelimit-service     http://localhost:8084/swagger-ui.html
echo   cache-service         http://localhost:8085/swagger-ui.html
echo   scheduler-service     http://localhost:8086/swagger-ui.html
echo.
echo All client traffic should go through gateway-service (port 8080) - the
echo other 6 ports are for direct/local debugging only.
echo.
echo Close each window (or Ctrl+C inside it) to stop that service.
