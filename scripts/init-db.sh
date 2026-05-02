#!/bin/bash
# Creates both databases on first container start.
# Postgres only runs initdb scripts when the data directory is empty (first boot).
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" << 'EOSQL'
    SELECT 'CREATE DATABASE pooler_bank'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'pooler_bank')\gexec

    SELECT 'CREATE DATABASE keycloak'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keycloak')\gexec
EOSQL

echo "Databases pooler_bank and keycloak are ready."
