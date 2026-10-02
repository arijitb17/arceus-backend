#!/usr/bin/env bash
# Builds every module once, then starts all 9 services in the background.
# Logs go to ./logs/<service>.log - tail them to see what's actually happening.
#
# This assumes you already have Postgres, Redis, and RabbitMQ running and
# reachable per your .env (this script only starts the Spring Boot services
# themselves). For a true one-command demo with infra included, use
# `docker compose up --build` from the repo root instead - that's the
# recommended way to run this for anyone who isn't actively developing it.
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

# scheduler-service first, always - auth-service/cache-service/
# notification-service each register a job with it at startup
# (SessionCleanupJobRegistrar/SweepJobRegistrar/RetryJobRegistrar), and if
# scheduler-service isn't listening yet when they try, that registration
# fails outright (logged as an ERROR, not fatal - the service still starts
# fine, but that job never gets registered until the next restart). This
# doesn't block on scheduler-service actually finishing startup before
# moving on - it's still a race, just one this ordering makes far less
# likely to lose, since scheduler-service has a real head start.
start services/scheduler-service scheduler-service
sleep 5

start services/auth-service auth-service
start services/notification-service notification-service
start services/ratelimit-service ratelimit-service
start services/cache-service cache-service
start services/feature-flag-service feature-flag-service

# organization-service before the Gateway too - it's a gRPC target of
# gateway-service's ProxyController (/api/organizations/**) the same way
# auth/notification/cache are, and the Developer Portal is the whole
# reason it exists.
start services/organization-service organization-service

# Gateway next-to-last - it's the front door and dials out to the other
# services over gRPC (channels connect lazily, so strict ordering isn't
# required, but starting it after them means its /health check has
# something to report on immediately).
start services/gateway-service gateway-service

# monitoring-service last - it polls every other service's Actuator
# endpoints (see arceus.monitoring.targets in its application.yml), so
# there's nothing useful for it to observe until everyone else is up.
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
