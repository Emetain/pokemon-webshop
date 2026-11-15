#!/usr/bin/env bash
# One-time setup of the server for Poketopia. Run as root from a copy of this folder:
#
#   ./setup.sh '<public key of the GitHub Actions deploy key>'
#
# Safe to run again: it adds what is missing, updates the scripts, and never overwrites /opt/poketopia/.env or
# /opt/poketopia/ops.env. It doesn't touch the firewall or SSH: those are separate steps in docs/phases/phase-07.md,
# because they can lock you out.
set -euo pipefail
cd "$(dirname "$0")"

pubkey=${1:?usage: setup.sh '<deploy public key>'}
[[ $pubkey == ssh-* ]] || { echo "That doesn't look like a public key." >&2; exit 1; }

ROOT=/opt/poketopia
BACKUPS=/var/backups/poketopia

# Docker Compose from Ubuntu's own packages, like Docker itself. restic for the copy of the backups elsewhere, and
# unattended-upgrades for the automatic security updates (Ubuntu has it by default; this makes sure).
missing=()
for package in docker-compose-v2 restic unattended-upgrades; do
  dpkg -s "$package" >/dev/null 2>&1 || missing+=("$package")
done
if ((${#missing[@]})); then
  apt-get update -q
  apt-get install -y -q "${missing[@]}"
fi

# Security updates every day, without rebooting by themselves: the server also runs Minecraft. When an update needs
# a reboot, /var/run/reboot-required exists: reboot at a quiet moment (the shop comes back by itself, Minecraft is
# started by hand). An update of Docker itself restarts the shop's containers, about a minute down.
if [[ ! -f /etc/apt/apt.conf.d/20auto-upgrades ]] || ! grep -q 'Unattended-Upgrade "1"' /etc/apt/apt.conf.d/20auto-upgrades; then
  printf 'APT::Periodic::Update-Package-Lists "1";\nAPT::Periodic::Unattended-Upgrade "1";\n' >/etc/apt/apt.conf.d/20auto-upgrades
  echo "Switched on automatic security updates."
fi

# A user for the deploys. It runs Docker, and nothing else logs in with it.
id deploy >/dev/null 2>&1 || useradd --create-home --shell /bin/bash deploy
usermod --append --groups docker deploy

# The scripts belong to root, the versions and the .env to the deploy user.
install -d -o root -g root -m 755 "$ROOT" "$ROOT/bin"
install -d -o deploy -g deploy -m 755 "$ROOT/releases"
install -o root -g root -m 755 deploy.sh compose.sh backup.sh make-admin.sh restore-check.sh shop-access.sh \
  disk-check.sh offsite.sh "$ROOT/bin/"

# Only its owner can read the .env. The database password is generated here and never shown.
if [[ ! -f $ROOT/.env ]]; then
  install -o deploy -g deploy -m 600 /dev/null "$ROOT/.env"
  sed "s|^POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=$(openssl rand -hex 32)|" env.example >"$ROOT/.env"
  echo "Created $ROOT/.env. Fill in SITE_ADDRESS, APP_BASE_URL, MAIL_FROM and the preview login."
fi

# Settings for the copy of the backups elsewhere and the monitoring pings: root only, so the deploy user can't reach
# the copies elsewhere. Everything in it is optional.
if [[ ! -f $ROOT/ops.env ]]; then
  install -o root -g root -m 600 ops.env.example "$ROOT/ops.env"
  echo "Created $ROOT/ops.env. Fill it in to keep backups elsewhere and get monitoring mails (docs/phases/phase-10.md)."
fi

# Backups outside the Docker volumes. The deploy user only writes the copies from just before a deploy.
install -d -o root -g root -m 711 "$BACKUPS"
install -d -o root -g root -m 700 "$BACKUPS/db" "$BACKUPS/media"
install -d -o deploy -g deploy -m 700 "$BACKUPS/pre-deploy"

# The daily backup, and the hourly disk check.
install -o root -g root -m 644 poketopia-backup.service poketopia-backup.timer poketopia-disk-check.service \
  poketopia-disk-check.timer /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now poketopia-backup.timer poketopia-disk-check.timer

# The deploy key may only run deploy.sh: no shell, no port forwarding.
install -d -o deploy -g deploy -m 700 /home/deploy/.ssh
keys=/home/deploy/.ssh/authorized_keys
line="command=\"$ROOT/bin/deploy.sh\",restrict $pubkey"
touch "$keys"
grep -qxF "$line" "$keys" || echo "$line" >>"$keys"
chown deploy:deploy "$keys"
chmod 600 "$keys"

echo "Done."
