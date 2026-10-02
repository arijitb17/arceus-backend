#!/bin/bash
# Runs automatically on first container start (postgres:16 entrypoint picks
# up every *.sh in /docker-entrypoint-initdb.d/). Creates one database per
# service - they all share this one Postgres instance/user in the demo
# compose file, but stay logically separate.
set -e

for db in arceus_auth arceus_notification arceus_scheduler arceus_feature_flag arceus_organization arceus_monitoring; do
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-SQL
    SELECT 'CREATE DATABASE $db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$db')\gexec
SQL
done
