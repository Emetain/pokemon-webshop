#!/usr/bin/env bash
# Runs docker compose on the version that is live, for example:
#
#   /opt/poketopia/bin/compose.sh ps
#   /opt/poketopia/bin/compose.sh logs --follow backend
#   /opt/poketopia/bin/compose.sh restart backend
set -euo pipefail

ROOT=${POKETOPIA_ROOT:-/opt/poketopia}
current=$ROOT/releases/current
[[ -L $current ]] || { echo "No version is live yet." >&2; exit 1; }

IMAGE_TAG=$(basename "$(readlink "$current")") exec docker compose --project-name poketopia \
  --file "$current/compose.yml" --env-file "$ROOT/.env" "$@"
