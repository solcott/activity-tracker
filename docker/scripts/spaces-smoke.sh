#!/usr/bin/env bash
# Checks that the local PDS serves atproto Spaces: as <name>.test, creates (or reuses) a member-list space, writes a record into it,
# reads it back as the owner, confirms an anonymous read is refused, and deletes the record. Safe to rerun.
# Usage: docker/scripts/spaces-smoke.sh alice [pds-url]
set -euo pipefail

cd "$(dirname "$0")/.."
[[ -f .env ]] || { echo "docker/.env missing: cp docker/.env.example docker/.env" >&2; exit 1; }
# shellcheck disable=SC1091
source .env

name="${1:?usage: spaces-smoke.sh <name> [pds-url]}"
pds="${2:-http://localhost:2583}"
space_type="app.activitytracker.smoke"
collection="app.activitytracker.smoke.note"

# Runs one XRPC call and prints its body. On a non-2xx it names the call and exits, unless the error is in $allow (space-separated).
# Usage: xrpc <method> <GET|POST> [curl args...]
allow=""
xrpc() {
  local method=$1 verb=$2 out status
  shift 2
  out=$(curl -sS -w '\n%{http_code}' -X "$verb" "$@" "$pds/xrpc/$method") || { echo "FAIL $method: no response from $pds" >&2; exit 1; }
  status=${out##*$'\n'}
  out=${out%$'\n'*}
  if [[ $status != 2* ]]; then
    local error
    error=$(sed -nE 's/.*"error":"([^"]+)".*/\1/p' <<<"$out")
    if [[ -n $error && " $allow " == *" $error "* ]]; then
      echo "$out"
      return
    fi
    echo "FAIL $method: HTTP $status ${out:0:300}" >&2
    exit 1
  fi
  echo "$out"
}
json_field() { sed -nE "s/.*\"$1\":\"([^\"]+)\".*/\1/p"; }
urlencode() { python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$1"; }

session=$(xrpc com.atproto.server.createSession POST -H 'Content-Type: application/json' \
  -d "{\"identifier\":\"$name.test\",\"password\":\"$TEST_ACCOUNT_PASSWORD\"}")
did=$(json_field did <<<"$session")
auth=(-H "Authorization: Bearer $(json_field accessJwt <<<"$session")")
space="at://$did/space/$space_type/smoke"
echo "ok   signed in as $name.test ($did)"

allow="SpaceAlreadyExists"
created=$(xrpc com.atproto.simplespace.createSpace POST "${auth[@]}" -H 'Content-Type: application/json' -d "{
  \"spaceType\":\"$space_type\",\"skey\":\"smoke\",
  \"readPolicy\":{\"\$type\":\"com.atproto.simplespace.defs#memberListPolicy\"},
  \"writePolicy\":{\"\$type\":\"com.atproto.simplespace.defs#memberListPolicy\"},
  \"appAccess\":{\"\$type\":\"com.atproto.simplespace.defs#open\"}}")
allow=""
[[ $created == *SpaceAlreadyExists* ]] && echo "ok   createSpace: already exists" || echo "ok   createSpace: $(json_field uri <<<"$created")"

xrpc com.atproto.simplespace.getSpace GET "${auth[@]}" -G --data "space=$(urlencode "$space")" >/dev/null
echo "ok   getSpace"

marker="smoke-$(date +%s)"
xrpc com.atproto.space.putRecord POST "${auth[@]}" -H 'Content-Type: application/json' -d "{
  \"space\":\"$space\",\"repo\":\"$did\",\"collection\":\"$collection\",\"rkey\":\"self\",
  \"record\":{\"\$type\":\"$collection\",\"text\":\"$marker\"}}" >/dev/null
echo "ok   putRecord"

query=(-G --data "space=$(urlencode "$space")" --data "repo=$did" --data "collection=$collection" --data "rkey=self")
record=$(xrpc com.atproto.space.getRecord GET "${auth[@]}" "${query[@]}")
[[ $record == *"\"text\":\"$marker\""* ]] || { echo "FAIL com.atproto.space.getRecord: value didn't round-trip: ${record:0:300}" >&2; exit 1; }
echo "ok   getRecord as owner"

allow="AuthMissing AuthRequired"
anonymous=$(xrpc com.atproto.space.getRecord GET "${query[@]}")
allow=""
[[ $anonymous == *'"error"'* ]] || { echo "FAIL com.atproto.space.getRecord: anonymous read wasn't refused" >&2; exit 1; }
echo "ok   getRecord refused without credentials"

xrpc com.atproto.space.deleteRecord POST "${auth[@]}" -H 'Content-Type: application/json' \
  -d "{\"space\":\"$space\",\"repo\":\"$did\",\"collection\":\"$collection\",\"rkey\":\"self\"}" >/dev/null
echo "ok   deleteRecord"
echo "Spaces smoke check passed on $pds"
