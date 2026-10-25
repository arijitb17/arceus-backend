#!/usr/bin/env bash

set -euo pipefail
cd "$(dirname "$0")/.."

ENV_FILE=".env"
AUTH_BASE_URL="${AUTH_BASE_URL:-http://localhost:8081}"

ADMIN_EMAIL="admin@arceus.com"
ADMIN_PASSWORD="admin123"
ADMIN_NAME="Admin"

USER_NAME="Arijit"
USER_EMAIL="ariban017@gmail.com"
USER_PHONE="9957777157"
USER_PASSWORD="arijit123"

echo "==> Setting SUPER_ADMIN_* in $ENV_FILE"

set_env_var() {
  local key="$1" value="$2"
  if grep -q "^${key}=" "$ENV_FILE" 2>/dev/null; then
    local escaped
    escaped=$(printf '%s' "$value" | sed -e 's/[&\\]/\\&/g')
    sed -i.bak "s/^${key}=.*/${key}=${escaped}/" "$ENV_FILE"
  else
    if [ -s "$ENV_FILE" ] && [ "$(tail -c1 "$ENV_FILE" | wc -l)" -eq 0 ]; then
      echo "" >> "$ENV_FILE"
    fi
    echo "${key}=${value}" >> "$ENV_FILE"
  fi
}

touch "$ENV_FILE"
set_env_var "SUPER_ADMIN_EMAIL" "$ADMIN_EMAIL"
set_env_var "SUPER_ADMIN_PASSWORD" "$ADMIN_PASSWORD"
set_env_var "SUPER_ADMIN_NAME" "$ADMIN_NAME"
rm -f "$ENV_FILE.bak"

echo "    SUPER_ADMIN_EMAIL=$ADMIN_EMAIL"
echo "    SUPER_ADMIN_PASSWORD=(set)"
echo "    SUPER_ADMIN_NAME=$ADMIN_NAME"
echo "    -> takes effect the next time auth-service starts (idempotent;"
echo "       does nothing if that email already exists)."

echo ""
echo "==> Registering regular user via POST $AUTH_BASE_URL/api/auth/register"

http_status=$(curl -s -o /tmp/seed-user-response.json -w "%{http_code}" \
  -X POST "$AUTH_BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"${USER_NAME}\",\"email\":\"${USER_EMAIL}\",\"phoneNumber\":\"${USER_PHONE}\",\"password\":\"${USER_PASSWORD}\"}") || {
    echo "    Could not reach $AUTH_BASE_URL - is auth-service running?"
    echo "    (Set AUTH_BASE_URL=... if it's on a different host/port.)"
    exit 1
  }

echo "    HTTP $http_status"
cat /tmp/seed-user-response.json
echo ""

if [ "$http_status" = "201" ]; then
  echo "    -> Registered. This account still needs email + phone OTP"
  echo "       verification (POST /api/auth/verify-email and"
  echo "       /api/auth/verify-phone) before it can log in - that's"
  echo "       enforced deliberately (User.isFullyVerified()) and can't be"
  echo "       skipped from outside the app without touching the database"
  echo "       directly."
elif [ "$http_status" = "409" ]; then
  echo "    -> Account already exists - nothing to do."
else
  echo "    -> Registration did not succeed, see response above"
  echo "       (common causes: auth-service not up yet, or the feature"
  echo "       flag gating registration is off)."
fi

echo ""
echo "==> Done. Start/restart the stack (./scripts/run-all.sh) for the"
echo "    admin account to be created."