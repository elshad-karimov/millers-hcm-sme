#!/usr/bin/env bash
# pg_dump_docker.sh — dev-only wrapper that runs pg_dump INSIDE the hcm-postgres
# Docker container, avoiding client/server version mismatches on developer machines.
#
# Usage: set hcm.backup.pg-dump-command to the absolute path of this script,
# set hcm.backup.pg-dump-port-override=5432 (the container-internal port),
# and optionally hcm.backup.pg-dump-host-override=localhost.
#
# Or via env vars:
#   HCM_BACKUP_PG_DUMP_CMD=/path/to/pg_dump_docker.sh
#   HCM_BACKUP_PG_DUMP_PORT=5432
exec docker exec \
  -i \
  -e PGPASSWORD="${PGPASSWORD}" \
  hcm-postgres \
  pg_dump "$@"
