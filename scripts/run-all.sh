#!/usr/bin/env bash
# Builds every module once, then starts all 4 services in the background.
# Logs go to ./logs/<service>.log - tail them to see what's actually happening.
#
# Assumes Postgres, Redis, and RabbitMQ are already running and reachable
# per your .env - this script does NOT start infrastructure, only the
# Spring Boot services themselves (Docker Compose for infra is intentionally
# deferred - see the roadmap doc).
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

start services/auth-service auth-service
start services/notification-service notification-service
start services/audit-service audit-service
start services/ratelimit-service ratelimit-service

# Gateway last - it's the front door and dials out to the other 4 over gRPC
# (channels connect lazily, so strict ordering isn't required, but starting
# it last means its /health check has something to report on immediately).
start services/gateway-service gateway-service

cat <<'EOF'

All 5 services are starting in the background:
  gateway-service       http://localhost:8080/health
  auth-service          http://localhost:8081/swagger-ui.html
  notification-service  http://localhost:8082/swagger-ui.html
  audit-service         http://localhost:8083/swagger-ui.html
  ratelimit-service     http://localhost:8084/swagger-ui.html

All client traffic should go through gateway-service (port 8080) - the
other 4 ports are for direct/local debugging only.

Tail a log:    tail -f logs/gateway-service.log
Stop them all: pkill -f spring-boot:run
EOF
