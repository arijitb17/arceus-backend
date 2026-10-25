#!/bin/bash
set -e

for db in arceus_auth arceus_notification arceus_scheduler arceus_feature_flag arceus_organization arceus_monitoring; do
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-SQL
    SELECT 'CREATE DATABASE $db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$db')\gexec
SQL
done
