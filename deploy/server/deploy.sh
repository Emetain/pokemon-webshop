#!/usr/bin/env bash
# Deploys one version of Poketopia, and goes back to the previous version when the new one doesn't start.
#
#   deploy.sh <commit sha>
#
# GitHub Actions runs it over SSH as the deploy user. That key may only run this script (a forced command in
# ~deploy/.ssh/authorized_keys), so the commit arrives in SSH_ORIGINAL_COMMAND, and a registry token that only works
# while that workflow runs arrives on stdin. To go back to an earlier version by hand, as root:
#
#   sudo -u deploy /opt/poketopia/bin/deploy.sh <commit sha>
#
# A rollback can't undo database migrations the newer version already ran. See docs/phases/phase-07.md.
set -euo pipefail

ROOT=${POKETOPIA_ROOT:-/opt/poketopia}
BACKUPS=${POKETOPIA_BACKUPS:-/var/backups/poketopia}
REGISTRY=${POKETOPIA_REGISTRY:-ghcr.io/emetain}
BACKEND_IMAGE=$REGISTRY/pokemon-webshop-backend
WEB_IMAGE=$REGISTRY/pokemon-webshop-web
RELEASES=$ROOT/releases
# Releases and images kept for rollbacks by hand, besides the live one.
KEEP_RELEASES=5
KEEP_DUMPS=10
WAIT_SECONDS=${POKETOPIA_WAIT_SECONDS:-300}

log() { printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }
fail() { log "$*" >&2; exit 1; }

tag=${1:-${SSH_ORIGINAL_COMMAND:-}}
if [[ ! $tag =~ ^[0-9a-f]{40}$ ]]; then
  echo "usage: deploy.sh <40-character commit sha>" >&2
  exit 2
fi

umask 027
cd "$ROOT"

# One deploy at a time. A second one waits for the first.
exec 9>"$RELEASES/.deploy.lock"
flock --wait 900 9 || fail "Another deploy is still running."

# The registry token, when one comes in on stdin. It is only used for this pull and never saved.
token=""
if [[ ! -t 0 ]]; then
  IFS= read -r token || true
fi
docker_config=""
cleanup() { [[ -z $docker_config ]] || rm -rf "$docker_config"; }
trap cleanup EXIT

compose() {
  local version=$1
  shift
  IMAGE_TAG=$version docker compose --project-name poketopia \
    --file "$RELEASES/$version/compose.yml" --env-file "$ROOT/.env" "$@"
}

container_of() {
  docker ps --quiet --filter label=com.docker.compose.project=poketopia --filter "label=com.docker.compose.service=$1"
}

live_version() {
  if [[ -L $RELEASES/current ]]; then basename "$(readlink "$RELEASES/current")"; fi
}

pull_images() {
  local missing=() image output
  for image in "$BACKEND_IMAGE:$tag" "$WEB_IMAGE:$tag"; do
    docker image inspect "$image" >/dev/null 2>&1 || missing+=("$image")
  done
  ((${#missing[@]})) || return 0

  # A Docker config of its own, so the login never stays behind on the server.
  docker_config=$(mktemp -d)
  if [[ -n $token ]]; then
    output=$(printf '%s' "$token" | DOCKER_CONFIG=$docker_config docker login ghcr.io --username deploy --password-stdin 2>&1) \
      || fail "Logging in to the registry failed: $output"
  fi
  for image in "${missing[@]}"; do
    log "Pulling $image"
    DOCKER_CONFIG=$docker_config docker pull --quiet "$image" >/dev/null
  done
}

prepare_release() {
  local dir=$RELEASES/$tag container
  mkdir -p "$dir"
  # Each version brings its own compose file, inside its web image.
  container=$(docker create "$WEB_IMAGE:$tag")
  docker cp --quiet "$container:/etc/poketopia/compose.yml" "$dir/compose.yml"
  docker rm "$container" >/dev/null
}

dump_database() {
  # A copy of the database from just before the new version runs its migrations, because a rollback can't undo them.
  local postgres file
  postgres=$(container_of postgres)
  [[ -n $postgres ]] || return 0
  file=$BACKUPS/pre-deploy/$(date '+%Y-%m-%d_%H%M%S')_before_${tag:0:12}.dump
  log "Saving the database to $file"
  docker exec "$postgres" sh -c 'pg_dump --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --format=custom' >"$file.partial"
  mv "$file.partial" "$file"
  find "$BACKUPS/pre-deploy" -maxdepth 1 -name '*.dump' -printf '%T@ %p\n' | sort -rn | tail -n +$((KEEP_DUMPS + 1)) \
    | cut -d' ' -f2- | xargs --no-run-if-empty rm --
}

start() {
  log "Starting version $1"
  compose "$1" up --detach --wait --wait-timeout "$WAIT_SECONDS" --remove-orphans
}

prune() {
  local live old
  live=$(live_version)
  find "$RELEASES" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %f\n' | sort -rn | tail -n +$((KEEP_RELEASES + 2)) \
    | cut -d' ' -f2 | while read -r old; do
      [[ $old != "$live" ]] || continue
      log "Removing old version $old"
      docker image rm "$BACKEND_IMAGE:$old" "$WEB_IMAGE:$old" >/dev/null 2>&1 || true
      rm -rf "${RELEASES:?}/$old"
    done
}

previous=$(live_version)
pull_images
prepare_release
dump_database

if start "$tag"; then
  ln -sfn "$tag" "$RELEASES/current.new"
  mv -T "$RELEASES/current.new" "$RELEASES/current"
  log "Version $tag is live."
  prune
  exit 0
fi

log "Version $tag did not start. The last lines of its backend log:"
compose "$tag" logs --no-color --tail 80 backend || true
if [[ -n $previous && $previous != "$tag" ]]; then
  log "Going back to version $previous"
  if start "$previous"; then
    log "Back on version $previous. Version $tag is not live."
  else
    log "Version $previous did not start either. Look at: $ROOT/bin/compose.sh logs backend"
  fi
else
  log "There is no earlier version to go back to."
fi
exit 1
