#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."

echo "==> Building all modules (shared libs first, via reactor order)..."
./mvnw -q clean install -DskipTests

mkdir -p logs

start() {
  local module=$1
  local name=$2
  echo "==> Starting $name..."
  nohup ./mvnw -pl "$module" spring-boot:run > "logs/$name.log" 2>&1 &
  echo "$name PID: $!"
}

start services/scheduler-service scheduler-service
sleep 5

start services/auth-service auth-service
start services/notification-service notification-service
start services/ratelimit-service ratelimit-service
start services/cache-service cache-service
start services/feature-flag-service feature-flag-service

start services/organization-service organization-service

start services/gateway-service gateway-service

start services/monitoring-service monitoring-service

cat <<'EOF2'

All 9 services are starting in the background:
  gateway-service       http://localhost:8090/health
  auth-service          http://localhost:8081/swagger-ui.html
  notification-service  http://localhost:8082/swagger-ui.html
  ratelimit-service     http://localhost:8084/swagger-ui.html
  cache-service         http://localhost:8085/swagger-ui.html
  scheduler-service     http://localhost:8086/swagger-ui.html
  feature-flag-service  http://localhost:8087/swagger-ui.html
  monitoring-service    http://localhost:8088/swagger-ui.html  (platform dashboard: /dashboard)
  organization-service  http://localhost:8091/swagger-ui.html

All client traffic should go through gateway-service - the other ports are
for direct/local debugging only.

Tail a log:    tail -f logs/gateway-service.log
Stop them all: pkill -f spring-boot:run
EOF2
