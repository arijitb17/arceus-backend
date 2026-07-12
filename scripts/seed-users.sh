#!/usr/bin/env bash
#
# Seeds two accounts for local/dev use:
#
#   1. Admin  : admin@arceus.com / admin123
#   2. User   : ariban017@gmail.com / 9957777157 / arijit123
#
# WHY THIS SCRIPT WORKS THE WAY IT DOES
# --------------------------------------
# This codebase deliberately has no "create admin" or "create user" API for
# an operator to call - see the javadoc on SuperAdminBootstrap.java:
#
#   "The platform has no other way to ever create a ROLE_ADMIN user - there's
#    no promote-to-admin endpoint, and there shouldn't be one reachable over
#    the network without one already existing."
#
# So instead of writing raw SQL that bypasses the app's own rules (password
# hashing, verification flags, uniqueness checks), this script uses the two
# paths the app actually supports:
#
#   - Admin  -> SUPER_ADMIN_EMAIL / SUPER_ADMIN_PASSWORD / SUPER_ADMIN_NAME
#               env vars, read once by SuperAdminBootstrap on auth-service
#               startup. Idempotent: if the account already exists it's a
#               no-op, so re-running this script (or restarting the
#               service) never resets an existing password.
#   - User   -> POST /api/auth/register, the real signup endpoint, so the
#               account goes through normal validation.
#
# USAGE
# -----
#   ./scripts/seed-users.sh
#
# Run this BEFORE starting auth-service for the first time (the admin part
# only takes effect on startup). If auth-service is already running, this
# script will still register the regular user immediately, but you'll need
# to restart auth-service (after setting the env vars) to get the admin
# account created.

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

# ---------------------------------------------------------------------------
# 1. Admin - write SUPER_ADMIN_* into .env (created via SuperAdminBootstrap
#    the next time auth-service starts up).
# ---------------------------------------------------------------------------
echo "==> Setting SUPER_ADMIN_* in $ENV_FILE"

set_env_var() {
  local key="$1" value="$2"
  if grep -q "^${key}=" "$ENV_FILE" 2>/dev/null; then
    # Escape & and \ so sed's replacement text doesn't choke on them.
    local escaped
    escaped=$(printf '%s' "$value" | sed -e 's/[&\\]/\\&/g')
    sed -i.bak "s/^${key}=.*/${key}=${escaped}/" "$ENV_FILE"
  else
    # If the file's last byte isn't a newline, appending here would glue
    # this line onto the end of the previous one (silently corrupting
    # both) instead of starting a new line - add one first if needed.
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

# ---------------------------------------------------------------------------
# 2. Regular user - register through the real endpoint.
# ---------------------------------------------------------------------------
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