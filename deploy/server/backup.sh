#!/usr/bin/env bash
# Daily backup of the database and the uploaded images, kept for 30 days. poketopia-backup.timer runs it as root.
#
# The backups stay on this server, outside the Docker volumes: the owner's choice. They are lost with the server.
# Restoring is described in docs/phases/phase-07.md, and restore-check.sh tests a backup without touching the shop.
set -euo pipefail

BACKUPS=${POKETOPIA_BACKUPS:-/var/backups/poketopia}
KEEP_DAYS=30

log() { printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }

umask 077
stamp=$(date '+%Y-%m-%d_%H%M')
mkdir -p "$BACKUPS/db" "$BACKUPS/media"

postgres=$(docker ps --quiet --filter label=com.docker.compose.project=poketopia --filter label=com.docker.compose.service=postgres)
[[ -n $postgres ]] || { log "The database isn't running: no backup." >&2; exit 1; }

# The database in pg_dump's own format: compressed, and pg_restore can restore it into any empty database.
dump=$BACKUPS/db/$stamp.dump
docker exec "$postgres" sh -c 'pg_dump --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --format=custom' >"$dump.partial"
mv "$dump.partial" "$dump"
log "Database: $dump ($(du -h "$dump" | cut -f1))"

# The images: a full folder per day. Files that didn't change are hard links to the day before, so a day only takes
# the space of what changed.
media=$(docker volume inspect --format '{{ .Mountpoint }}' poketopia_media)
previous=$(find "$BACKUPS/media" -mindepth 1 -maxdepth 1 -type d -name '20*' ! -name '*.partial' | sort | tail -n 1)
rsync --archive --delete ${previous:+--link-dest="$previous"} "$media/" "$BACKUPS/media/$stamp.partial/"
mv "$BACKUPS/media/$stamp.partial" "$BACKUPS/media/$stamp"
log "Images: $BACKUPS/media/$stamp ($(find "$BACKUPS/media/$stamp" -type f | wc -l) files)"

# Keep 30 days, by the date in the name.
cutoff=$(date --date="$KEEP_DAYS days ago" '+%Y-%m-%d')
for old in "$BACKUPS"/db/20*.dump "$BACKUPS"/media/20*; do
  [[ -e $old ]] || continue
  name=$(basename "$old")
  if [[ ${name:0:10} < $cutoff ]]; then
    log "Removing $old"
    rm -rf "$old"
  fi
done
