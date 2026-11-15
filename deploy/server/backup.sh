#!/usr/bin/env bash
# Daily backup of the database and the uploaded images, kept for 30 days. poketopia-backup.timer runs it as root.
#
# The backups are kept on this server, outside the Docker volumes. When RESTIC_REPOSITORY is set in
# /opt/poketopia/ops.env (systemd passes it in), that day's dump and image folder also go to that repository somewhere
# else, encrypted, and are kept there for BACKUP_OFFSITE_KEEP (30 days). With BACKUP_PING_URL set, a monitoring
# service hears whether the backup worked. Restoring is described in docs/phases/phase-07.md and phase-10.md, and
# restore-check.sh tests a backup without touching the shop.
set -euo pipefail

BACKUPS=${POKETOPIA_BACKUPS:-/var/backups/poketopia}
KEEP_DAYS=30

log() { printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }

# Tells the monitoring service how it went. A ping that doesn't arrive must never fail the backup itself.
ping_monitor() {
  [[ -n ${BACKUP_PING_URL:-} ]] || return 0
  curl --fail --silent --show-error --max-time 10 --retry 3 --output /dev/null "${BACKUP_PING_URL%/}$1" \
    || log "Could not reach BACKUP_PING_URL." >&2
}
trap 'status=$?; if ((status != 0)); then ping_monitor /fail; fi' EXIT

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

# The copy elsewhere: restic encrypts on this server and only sends what the repository doesn't have yet. One host
# name and tag for every snapshot, so the 30 days count across the daily folders, also after a move to another server.
if [[ -n ${RESTIC_REPOSITORY:-} ]]; then
  command -v restic >/dev/null || { log "RESTIC_REPOSITORY is set, but restic isn't installed (run setup.sh)." >&2; exit 1; }
  log "Copying to the repository elsewhere"
  restic backup --quiet --host poketopia --tag poketopia "$dump" "$BACKUPS/media/$stamp"
  restic forget --quiet --host poketopia --tag poketopia --group-by host,tags \
    --keep-within "${BACKUP_OFFSITE_KEEP:-30d}" --prune
  log "Copied elsewhere: $(restic snapshots --quiet --host poketopia --tag poketopia --compact | grep -c '^[0-9a-f]\{8\} ') snapshots kept there"
fi

ping_monitor ""
