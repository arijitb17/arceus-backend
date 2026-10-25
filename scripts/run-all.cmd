@echo off

cd /d "%~dp0\.."

echo ==> Building all modules (shared libs first, via reactor order)...
call mvnw.cmd -q clean install -DskipTests
if errorlevel 1 (
  echo Build failed - fix the error above before starting services.
  exit /b 1
)

start "scheduler-service"    cmd /k mvnw.cmd -pl services/scheduler-service spring-boot:run
timeout /t 5 /nobreak >nul

start "auth-service"         cmd /k mvnw.cmd -pl services/auth-service spring-boot:run
start "notification-service" cmd /k mvnw.cmd -pl services/notification-service spring-boot:run
start "ratelimit-service"    cmd /k mvnw.cmd -pl services/ratelimit-service spring-boot:run
start "cache-service"        cmd /k mvnw.cmd -pl services/cache-service spring-boot:run
start "feature-flag-service" cmd /k mvnw.cmd -pl services/feature-flag-service spring-boot:run

start "organization-service" cmd /k mvnw.cmd -pl services/organization-service spring-boot:run

start "gateway-service"      cmd /k mvnw.cmd -pl services/gateway-service spring-boot:run

start "monitoring-service"   cmd /k mvnw.cmd -pl services/monitoring-service spring-boot:run

echo.
echo All 9 services are starting, each in its own window:
echo   gateway-service       http://localhost:8090/health
echo   auth-service          http://localhost:8081/swagger-ui.html
echo   notification-service  http://localhost:8082/swagger-ui.html
echo   ratelimit-service     http://localhost:8084/swagger-ui.html
echo   cache-service         http://localhost:8085/swagger-ui.html
echo   scheduler-service     http://localhost:8086/swagger-ui.html
echo   feature-flag-service  http://localhost:8087/swagger-ui.html
echo   monitoring-service    http://localhost:8088/swagger-ui.html  (platform dashboard: /dashboard)
echo   organization-service  http://localhost:8091/swagger-ui.html
echo.
echo All client traffic should go through gateway-service (port 8090) - the
echo other 8 ports are for direct/local debugging only.
echo.
echo Close each window (or Ctrl+C inside it) to stop that service.
