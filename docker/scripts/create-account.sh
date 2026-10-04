#!/usr/bin/env bash
# Creates <name>.test on the local PDS and seeds an athlete profile record.
# Usage: docker/scripts/create-account.sh alice
set -euo pipefail

cd "$(dirname "$0")/.."
[[ -f .env ]] || { echo "docker/.env missing: cp docker/.env.example docker/.env" >&2; exit 1; }
# shellcheck disable=SC1091
source .env

name="${1:?usage: create-account.sh <name>}"
handle="${name}.test"
pds="http://localhost:2583"

# POSTs JSON and prints the response body; on a non-2xx, prints the PDS's error message and exits.
post() {
  local out status
  out=$(curl -sS -w '\n%{http_code}' -X POST -H 'Content-Type: application/json' "$@")
  status=${out##*$'\n'}
  out=${out%$'\n'*}
  if [[ $status != 2* ]]; then
    echo "PDS returned $status: $out" >&2
    exit 1
  fi
  echo "$out"
}

session=$(post "$pds/xrpc/com.atproto.server.createAccount" \
  -d "{\"handle\":\"$handle\",\"email\":\"$name@example.test\",\"password\":\"$TEST_ACCOUNT_PASSWORD\"}")

did=$(echo "$session" | sed -E 's/.*"did":"([^"]+)".*/\1/')
jwt=$(echo "$session" | sed -E 's/.*"accessJwt":"([^"]+)".*/\1/')

post "$pds/xrpc/com.atproto.repo.putRecord" -H "Authorization: Bearer $jwt" \
  -d "{\"repo\":\"$did\",\"collection\":\"app.activitytracker.actor.profile\",\"rkey\":\"self\",\"validate\":false,
       \"record\":{\"\$type\":\"app.activitytracker.actor.profile\",\"displayName\":\"$name\"}}" >/dev/null

echo "Created $handle ($did). Sign in with handle '$handle' and TEST_ACCOUNT_PASSWORD from docker/.env."
