#!/usr/bin/env bash
# Prints `"inviteCode":"<code>",` for a createAccount body when the local PDS requires invites (device mode sets
# PDS_INVITE_REQUIRED, because its hostname is public), minting a single-use code through the admin API; prints
# nothing otherwise. The account scripts splice it into their JSON.
# Usage: body="{$(docker/scripts/invite-code.sh)\"handle\":...}"
set -euo pipefail

cd "$(dirname "$0")/.."
# shellcheck disable=SC1091
source .env

pds="${PDS_URL:-http://localhost:2583}"

[[ $(curl -sS "$pds/xrpc/com.atproto.server.describeServer" | jq -r '.inviteCodeRequired // false') == true ]] || exit 0
code=$(curl -sS -f -u "admin:$PDS_ADMIN_PASSWORD" -X POST -H 'Content-Type: application/json' \
  -d '{"useCount":1}' "$pds/xrpc/com.atproto.server.createInviteCode" | jq -r .code) \
  || { echo "FAIL couldn't mint an invite code (admin:\$PDS_ADMIN_PASSWORD)" >&2; exit 1; }
printf '"inviteCode":"%s",' "$code"
