#!/usr/bin/env bash
# Proves that a backup works, without touching the shop: restores a database backup into a separate test database,
# starts the live version of the backend on it, and asks it for some data. Afterwards everything is removed again.
#
#   restore-check.sh                 the newest daily backup
#   restore-check.sh <file.dump>     a specific one, for example from /var/backups/poketopia/pre-deploy
#
# Run as root. For a real restore, see docs/phases/phase-07.md.
set -euo pipefail

ROOT=${POKETOPIA_ROOT:-/opt/poketopia}
BACKUPS=${POKETOPIA_BACKUPS:-/var/backups/poketopia}
REGISTRY=${POKETOPIA_REGISTRY:-ghcr.io/emetain}
TEST_DB=restore_check
CHECK=poketopia-restore-check

log() { printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }

dump=${1:-$(find "$BACKUPS/db" -maxdepth 1 -name '20*.dump' | sort | tail -n 1)}
[[ -f $dump ]] || { echo "No backup found." >&2; exit 1; }
version=$(basename "$(readlink "$ROOT/releases/current")")
postgres=$(docker ps --quiet --filter label=com.docker.compose.project=poketopia --filter label=com.docker.compose.service=postgres)
[[ -n $postgres ]] || { echo "The database isn't running." >&2; exit 1; }

psql_in() { docker exec -i "$postgres" sh -c 'psql --quiet --no-align --tuples-only --set=ON_ERROR_STOP=1 --username="$POSTGRES_USER" --dbname="$1"' sh "$1"; }

cleanup() {
  docker rm --force "$CHECK" >/dev/null 2>&1 || true
  docker exec "$postgres" sh -c "dropdb --if-exists --username=\"\$POSTGRES_USER\" $TEST_DB" 2>/dev/null || true
}
trap cleanup EXIT
cleanup

log "Restoring $dump into the test database $TEST_DB"
docker exec "$postgres" sh -c "createdb --username=\"\$POSTGRES_USER\" $TEST_DB"
docker exec -i "$postgres" sh -c "pg_restore --username=\"\$POSTGRES_USER\" --dbname=$TEST_DB --no-owner --exit-on-error" <"$dump"

count() { psql_in "$1" <<<"SELECT count(*) FROM $2"; }
for table in users cards card_sets products orders; do
  printf '  %-10s live %8s   restored %8s\n' "$table" "$(count "$(docker exec "$postgres" printenv POSTGRES_DB)" "$table")" "$(count $TEST_DB "$table")"
done

log "Starting backend version $version on the test database"
# No jobs that reach outside or change data, no mail to anyone: only the app on the restored data.
docker run --detach --name "$CHECK" --network poketopia_default --memory 1g \
  --env SPRING_PROFILES_ACTIVE=prod \
  --env JAVA_TOOL_OPTIONS=-Xmx768m \
  --env "DB_URL=jdbc:postgresql://postgres:5432/$TEST_DB" \
  --env "DB_USERNAME=$(docker exec "$postgres" printenv POSTGRES_USER)" \
  --env "DB_PASSWORD=$(docker exec "$postgres" printenv POSTGRES_PASSWORD)" \
  --env APP_BASE_URL=http://localhost \
  --env MAIL_HOST=localhost --env MAIL_FROM=restore-check@localhost \
  --env MEDIA_DIRECTORY=/tmp/media \
  --env SPRING_APPLICATION_JSON='{"app":{"catalog":{"sync-cron":"-","import-pokedex-on-start":false},"collection":{"snapshot-cron":"-"},"drops":{"tick-cron":"-"},"checkout":{"release-cron":"-","cart-cleanup-cron":"-"},"auth":{"cleanup-cron":"-"}}}' \
  "$REGISTRY/pokemon-webshop-backend:$version" >/dev/null

for _ in $(seq 60); do
  if docker exec "$CHECK" curl --fail --silent http://localhost:8080/api/actuator/health >/dev/null 2>&1; then
    log "The backend started on the restored database (Flyway and schema validation passed)."
    for game in pokemon magic lorcana one-piece; do
      sets=$(docker exec "$CHECK" curl --fail --silent "http://localhost:8080/api/v1/catalog/$game/sets" | { grep -o '"code"' || true; } | wc -l)
      printf '  %-10s %5s sets through the API\n' "$game" "$sets"
    done
    exit 0
  fi
  if [[ $(docker inspect --format '{{ .State.Running }}' "$CHECK") != true ]]; then break; fi
  sleep 5
done

log "The backend did not start on the restored database. Its log:"
docker logs --tail 80 "$CHECK"
exit 1
