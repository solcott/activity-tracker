#!/usr/bin/env bash
# Sets up the local admin of the AppView's admin site (admin/admin-site) on the local PDS: creates siteadmin.test if it doesn't exist and
# writes APPVIEW_ADMINS=<its DID>=operator to docker/.env, which the AppView's run task reads. Sign in at the AppView's /admin as
# siteadmin.test with TEST_ACCOUNT_PASSWORD. Safe to rerun, and scripts/dev.sh runs it every time, so a reset stack gets the account back.
# Prints its DID.
# Usage: docker/scripts/setup-admin.sh
set -euo pipefail

cd "$(dirname "$0")/.."
[[ -f .env ]] || { echo "docker/.env missing: cp docker/.env.example docker/.env" >&2; exit 1; }
# shellcheck disable=SC1091
source .env

pds="http://localhost:2583"
name="siteadmin" # the PDS reserves "admin" and "operator" as handles

(($# == 0)) || { echo "usage: setup-admin.sh" >&2; exit 1; }

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

[[ ${APPVIEW_ADMINS:-} == "$did=operator" ]] || set_env APPVIEW_ADMINS "$did=operator"
echo "$did"
