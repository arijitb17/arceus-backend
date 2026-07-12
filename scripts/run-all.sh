#!/usr/bin/env bash
# Builds every module once, then starts all 9 services in the background.
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
start services/cache-service cache-service
start services/scheduler-service scheduler-service
start services/feature-flag-service feature-flag-service

# Gateway next-to-last - it's the front door and dials out to the other
# services over gRPC (channels connect lazily, so strict ordering isn't
# required, but starting it after them means its /health check has
# something to report on immediately).
start services/gateway-service gateway-service

# monitoring-service last - it polls every other service's Actuator
# endpoints (see arceus.monitoring.targets in its application.yml), so
# there's nothing useful for it to observe until everyone else is up.
start services/monitoring-service monitoring-service

# deployment-service very last - it's a gRPC client of both scheduler-service
# (scheduled deploys) and monitoring-service (pre/post-deploy health
# verification), so starting it once both are already up means its own
# /health check has something meaningful to report on immediately (gRPC
# channels still connect lazily, so this ordering isn't strictly required).
start services/deployment-service deployment-service

cat <<'EOF'

All 10 services are starting in the background:
  gateway-service       http://localhost:8090/health
  auth-service          http://localhost:8081/swagger-ui.html
  notification-service  http://localhost:8082/swagger-ui.html
  audit-service         http://localhost:8083/swagger-ui.html
  ratelimit-service     http://localhost:8084/swagger-ui.html
  cache-service         http://localhost:8085/swagger-ui.html
  scheduler-service     http://localhost:8086/swagger-ui.html
  feature-flag-service  http://localhost:8087/swagger-ui.html
  monitoring-service    http://localhost:8088/swagger-ui.html  (platform dashboard: /dashboard)
  deployment-service    http://localhost:8089/swagger-ui.html

All client traffic should go through gateway-service - the other ports are
for direct/local debugging only.

Tail a log:    tail -f logs/gateway-service.log
Stop them all: pkill -f spring-boot:run
EOF
