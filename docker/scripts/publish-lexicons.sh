#!/usr/bin/env bash
# Publishes the repo's space-type lexicons (lexicons/**.json whose main def is "type": "space") as
# com.atproto.lexicon.schema records of lexicons.test, creating that account if it doesn't exist. The
# PDS resolves space declarations from this account when PDS_LEXICON_AUTHORITY_DID names it, which
# OAuth needs to grant a space: scope. Safe to rerun. Prints the account's DID on stdout.
# Usage: docker/scripts/publish-lexicons.sh
set -euo pipefail

cd "$(dirname "$0")/.."
[[ -f .env ]] || { echo "docker/.env missing: cp docker/.env.example docker/.env" >&2; exit 1; }
# shellcheck disable=SC1091
source .env

name="lexicons"
pds="http://localhost:2583"

# POSTs JSON and prints the response body; on a non-2xx, prints the call and the PDS's error and exits.
post() {
  local method=$1 out status
  shift
  out=$(curl -sS -w '\n%{http_code}' -X POST -H 'Content-Type: application/json' "$@" "$pds/xrpc/$method")
  status=${out##*$'\n'}
  out=${out%$'\n'*}
  if [[ $status != 2* ]]; then
    echo "FAIL $method: HTTP $status ${out:0:300}" >&2
    exit 1
  fi
  echo "$out"
}

login="{\"identifier\":\"$name.test\",\"password\":\"$TEST_ACCOUNT_PASSWORD\"}"
if ! session=$(post com.atproto.server.createSession -d "$login" 2>/dev/null); then
  session=$(post com.atproto.server.createAccount \
    -d "{\"handle\":\"$name.test\",\"email\":\"$name@example.test\",\"password\":\"$TEST_ACCOUNT_PASSWORD\"}")
  echo "created $name.test" >&2
fi
did=$(jq -r .did <<<"$session")
auth=(-H "Authorization: Bearer $(jq -r .accessJwt <<<"$session")")

published=()
while IFS= read -r file; do
  [[ $(jq -r '.defs.main.type' "$file") == space ]] || continue
  id=$(jq -r .id "$file")
  record=$(jq -c '{"$type": "com.atproto.lexicon.schema"} + .' "$file")
  post com.atproto.repo.putRecord "${auth[@]}" -d "{\"repo\":\"$did\",\"collection\":\"com.atproto.lexicon.schema\",
    \"rkey\":\"$id\",\"validate\":false,\"record\":$record}" >/dev/null
  echo "published $id" >&2
  published+=("$id")
done < <(find ../lexicons -name '*.json' | sort)

((${#published[@]} > 0)) || { echo "FAIL no space lexicons found under lexicons/" >&2; exit 1; }

# The PDS keeps each declaration it resolved in its database, across restarts, and grants new sessions' space: scopes from that
# copy until it refreshes on its own schedule, so a collection added here would be refused as ScopeMissingError. Drop the
# copies of what was just published; the next sign-in resolves them again.
docker compose -f compose.yaml exec -T -w /app/packages/pds pds node -e '
  const db = new (require("better-sqlite3"))("/pds/account.sqlite")
  const drop = db.prepare("delete from lexicon where nsid = ?")
  for (const nsid of process.argv.slice(1)) drop.run(nsid)
' "${published[@]}"
echo "$did"
