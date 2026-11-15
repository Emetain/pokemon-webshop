#!/usr/bin/env bash
# Warns when a disk the shop uses is DISK_WARN_PERCENT (80) full. poketopia-disk-check.timer runs it every hour as
# root, with the settings from /opt/poketopia/ops.env.
#
# With DISK_PING_URL set, a monitoring service hears it: the URL when all disks are fine, <URL>/fail when one is too
# full. The service mails the owner on a /fail, and also when the pings stop, for example because the server is down.
# Without it, the warning is only in the journal: journalctl -u poketopia-disk-check.
set -euo pipefail

LIMIT=${DISK_WARN_PERCENT:-80}
# The system, Docker (images, the database and uploads live in its volumes) and the backups.
PATHS=(/ /var/lib/docker /var/backups/poketopia)

ping_monitor() {
  [[ -n ${DISK_PING_URL:-} ]] || return 0
  curl --fail --silent --show-error --max-time 10 --retry 3 --output /dev/null "${DISK_PING_URL%/}$1" \
    || echo "Could not reach DISK_PING_URL." >&2
}

worst=0
report=()
for path in "${PATHS[@]}"; do
  [[ -e $path ]] || continue
  used=$(df --output=pcent "$path" | tail -n 1 | tr -dc '0-9')
  report+=("$path ${used}%")
  ((used > worst)) && worst=$used
done

if ((worst >= LIMIT)); then
  echo "A disk is ${worst}% full, the limit is ${LIMIT}%: ${report[*]}. Look at docs/phases/phase-10.md (disk full)." >&2
  ping_monitor /fail
  exit 1
fi
echo "Disks fine: ${report[*]}"
ping_monitor ""
