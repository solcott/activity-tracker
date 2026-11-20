#!/usr/bin/env bash
# Sets up the segment importer's service account (segments/segment-import) on the local PDS: creates importer.test if it doesn't exist and
# an app password for it, and writes SEGMENT_IMPORTER_DID, SEGMENT_IMPORTER_IDENTIFIER, SEGMENT_IMPORTER_PASSWORD and
# SEGMENT_IMPORTER_PDS_URL to docker/.env, which scripts/segment-import.sh and the AppView's run task read. A stored app password is
# kept while it still signs in. Safe to rerun, and scripts/dev.sh runs it every time, so a reset stack gets the account back. Prints its DID.
# Usage: docker/scripts/setup-importer.sh
set -euo pipefail

cd "$(dirname "$0")/.."
[[ -f .env ]] || {
  echo "docker/.env missing: cp docker/.env.example docker/.env" >&2
  exit 1
}
# shellcheck disable=SC1091
source .env

pds="http://localhost:2583"
name=importer

(($# == 0)) || {
  echo "usage: setup-importer.sh" >&2
  exit 1
}

# Sets KEY=VALUE in .env, replacing an existing line.
set_env() {
  if grep -q "^$1=" .env; then
    sed -i.bak "s|^$1=.*|$1=$2|" .env && rm .env.bak
  else
    printf '\n%s=%s\n' "$1" "$2" >>.env
  fi
}

# POSTs JSON to an XRPC method and prints the body; on a non-2xx, prints the call and the error and fails.
post() {
  local method=$1 out status
  shift
  out=$(curl -sS -w '\n%{http_code}' -X POST -H 'Content-Type: application/json' "$@" "$pds/xrpc/$method")
  status=${out##*$'\n'}
  out=${out%$'\n'*}
  if [[ $status != 2* ]]; then
    echo "FAIL $method: HTTP $status ${out:0:300}" >&2
    return 1
  fi
  echo "$out"
}

login="{\"identifier\":\"$name.test\",\"password\":\"$TEST_ACCOUNT_PASSWORD\"}"
if ! session=$(post com.atproto.server.createSession -d "$login" 2>/dev/null); then
  session=$(post com.atproto.server.createAccount \
    -d "{$(scripts/invite-code.sh)\"handle\":\"$name.test\",\"email\":\"$name@example.test\",\"password\":\"$TEST_ACCOUNT_PASSWORD\"}")
  echo "created $name.test" >&2
fi
did=$(jq -r .did <<<"$session")

# The importer signs in with an app password, never the account password.
if ! post com.atproto.server.createSession \
  -d "{\"identifier\":\"$name.test\",\"password\":\"${SEGMENT_IMPORTER_PASSWORD:-}\"}" >/dev/null 2>&1; then
  app_password=$(post com.atproto.server.createAppPassword -H "Authorization: Bearer $(jq -r .accessJwt <<<"$session")" \
    -d "{\"name\":\"importer-$(date +%s)\"}" | jq -r .password)
  set_env SEGMENT_IMPORTER_PASSWORD "$app_password"
  echo "created an app password for $name.test" >&2
fi
[[ ${SEGMENT_IMPORTER_IDENTIFIER:-} == "$name.test" ]] || set_env SEGMENT_IMPORTER_IDENTIFIER "$name.test"
[[ -n ${SEGMENT_IMPORTER_PDS_URL:-} ]] || set_env SEGMENT_IMPORTER_PDS_URL "$pds"
[[ ${SEGMENT_IMPORTER_DID:-} == "$did" ]] || set_env SEGMENT_IMPORTER_DID "$did"
echo "$did"
