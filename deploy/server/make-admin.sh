#!/usr/bin/env bash
# Makes an account an admin, or a customer again. The person registers on the site first.
#
#   make-admin.sh <email>          the first admin, and the friend who helps with the images
#   make-admin.sh --undo <email>   back to a normal customer account
#
# There are no demo accounts on the server. The change works right away, also for someone who is already logged in.
set -euo pipefail

role=ADMIN
if [[ ${1:-} == --undo ]]; then
  role=CUSTOMER
  shift
fi
email=${1:?usage: make-admin.sh [--undo] <email>}

postgres=$(docker ps --quiet --filter label=com.docker.compose.project=poketopia --filter label=com.docker.compose.service=postgres)
[[ -n $postgres ]] || { echo "The database isn't running." >&2; exit 1; }

# psql variables quote the values, so an address can't change the query.
changed=$(docker exec -i "$postgres" sh -c \
  'psql --quiet --no-align --tuples-only --set=ON_ERROR_STOP=1 --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --set=email="$1" --set=role="$2"' \
  sh "$email" "$role" <<'SQL'
UPDATE users SET role = :'role' WHERE email = lower(trim(:'email')) RETURNING email;
SQL
)

if [[ -z $changed ]]; then
  echo "No account with the address $email. Register on the site first." >&2
  exit 1
fi
echo "$changed is now $role."
