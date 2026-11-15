#!/usr/bin/env bash
# Runs restic on the repository elsewhere, with the settings from /opt/poketopia/ops.env. As root, for example:
#
#   offsite.sh init                                    once, to create the repository
#   offsite.sh snapshots                               the backups that are there
#   offsite.sh restore latest --target /root/restore   fetch the newest one (then see docs/phases/phase-10.md)
#   offsite.sh check                                   test that the repository is whole
set -euo pipefail

OPS_ENV=${POKETOPIA_OPS_ENV:-/opt/poketopia/ops.env}
[[ -r $OPS_ENV ]] || { echo "$OPS_ENV doesn't exist or can't be read: run as root." >&2; exit 1; }
command -v restic >/dev/null || { echo "restic isn't installed: run setup.sh." >&2; exit 1; }

set -a
# shellcheck source=/dev/null
. "$OPS_ENV"
set +a
[[ -n ${RESTIC_REPOSITORY:-} ]] || { echo "Set RESTIC_REPOSITORY in $OPS_ENV first." >&2; exit 1; }
exec restic "$@"
