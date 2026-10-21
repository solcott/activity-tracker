#!/usr/bin/env bash
# Sets up the local labeler (Ozone): creates moderation.test (its service account) and mod.test (a moderator),
# generates Ozone's label-signing key, adds #atproto_labeler and #atproto_label to moderation.test's DID document
# on the local PLC (signed with the PDS's rotation key), writes OZONE_SERVER_DID and OZONE_SIGNING_KEY_HEX to
# docker/.env, starts Ozone, and adds mod.test to its team as an admin. It also creates triage.test, the AppView's own account on the
# team with the read-only triage role (Ozone accepts only full account DIDs, not the AppView's did:web), and an app password for it, written
# to docker/.env as APPVIEW_TRIAGE_DID and APPVIEW_TRIAGE_PASSWORD for the AppView's run task. Safe to rerun. Prints the labeler's DID.
# Usage: docker/scripts/setup-labeler.sh
set -euo pipefail

cd "$(dirname "$0")/.."
[[ -f .env ]] || { echo "docker/.env missing: cp docker/.env.example docker/.env" >&2; exit 1; }
# shellcheck disable=SC1091
source .env

COMPOSE=(docker compose -f compose.yaml)
pds="http://localhost:2583"
ozone="http://localhost:3010"
admin_password=${OZONE_ADMIN_PASSWORD:-local-dev-ozone-admin}

(($# == 0)) || { echo "usage: setup-labeler.sh" >&2; exit 1; }

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
  local base=$1 method=$2 out status
  shift 2
  out=$(curl -sS -w '\n%{http_code}' -X POST -H 'Content-Type: application/json' "$@" "$base/xrpc/$method")
  status=${out##*$'\n'}
  out=${out%$'\n'*}
  if [[ $status != 2* ]]; then
    echo "FAIL $method: HTTP $status ${out:0:300}" >&2
    return 1
  fi
  echo "$out"
}

# Prints the DID of NAME.test on the local PDS, creating the account if it doesn't exist.
ensure_account() {
  local name=$1 session
  if ! session=$(post "$pds" com.atproto.server.createSession \
    -d "{\"identifier\":\"$name.test\",\"password\":\"$TEST_ACCOUNT_PASSWORD\"}" 2>/dev/null); then
    session=$(post "$pds" com.atproto.server.createAccount \
      -d "{\"handle\":\"$name.test\",\"email\":\"$name@example.test\",\"password\":\"$TEST_ACCOUNT_PASSWORD\"}")
    echo "created $name.test" >&2
  fi
  jq -r .did <<<"$session"
}

# Ozone's label-signing key: secp256k1, the private scalar as 64 hex chars (openssl's text dump, as for the AppView key).
if [[ ! ${OZONE_SIGNING_KEY_HEX:-} =~ ^[0-9a-f]{64}$ ]]; then
  key=$(openssl ecparam -name secp256k1 -genkey -noout | openssl ec -text -noout 2>/dev/null |
    sed -n '/^priv:/,/^pub:/p' | grep -v -e '^priv:' -e '^pub:' | tr -d ' :\n')
  key=$(printf '%064s' "${key: -64}" | tr ' ' 0)
  [[ $key =~ ^[0-9a-f]{64}$ ]] || { echo "FAIL couldn't generate a secp256k1 key with openssl" >&2; exit 1; }
  set_env OZONE_SIGNING_KEY_HEX "$key"
  OZONE_SIGNING_KEY_HEX=$key
  echo "generated OZONE_SIGNING_KEY_HEX" >&2
fi

# Ozone's database, a second one on appview-db (whose init only ran for a fresh volume).
if [[ -z $("${COMPOSE[@]}" exec -T appview-db psql -U appview -d appview -tAc "SELECT 1 FROM pg_database WHERE datname = 'ozone'") ]]; then
  "${COMPOSE[@]}" exec -T appview-db psql -U appview -d appview -qc "CREATE DATABASE ozone" >/dev/null
  echo "created database ozone" >&2
fi

did=$(ensure_account moderation)
mod_did=$(ensure_account mod)
triage_did=$(ensure_account triage)

# The AppView signs in as triage.test with an app password, never the account password. A stored one is kept while it still works.
if ! post "$pds" com.atproto.server.createSession \
  -d "{\"identifier\":\"$triage_did\",\"password\":\"${APPVIEW_TRIAGE_PASSWORD:-}\"}" >/dev/null 2>&1; then
  session=$(post "$pds" com.atproto.server.createSession -d "{\"identifier\":\"triage.test\",\"password\":\"$TEST_ACCOUNT_PASSWORD\"}")
  app_password=$(post "$pds" com.atproto.server.createAppPassword -H "Authorization: Bearer $(jq -r .accessJwt <<<"$session")" \
    -d "{\"name\":\"appview-$(date +%s)\"}" | jq -r .password)
  set_env APPVIEW_TRIAGE_PASSWORD "$app_password"
  echo "created an app password for triage.test" >&2
fi
[[ ${APPVIEW_TRIAGE_DID:-} == "$triage_did" ]] || set_env APPVIEW_TRIAGE_DID "$triage_did"

# Adds the labeler service and label key to the DID document, unless it already has them. Runs in the Ozone image,
# which carries @did-plc/lib and @atproto/crypto; the PDS's rotation key is the DID's rotation key.
"${COMPOSE[@]}" run --rm --no-deps -T --entrypoint node \
  -e DID="$did" -e LABEL_KEY="$OZONE_SIGNING_KEY_HEX" -e ROTATION_KEY="$PDS_PLC_ROTATION_KEY_K256_PRIVATE_KEY_HEX" \
  -e ENDPOINT="$ozone" ozone --input-type=module -e '
    import { Secp256k1Keypair } from "@atproto/crypto"
    import plc from "@did-plc/lib"
    const { DID, LABEL_KEY, ROTATION_KEY, ENDPOINT } = process.env
    const client = new plc.Client("http://plc:2582")
    const labelKey = (await Secp256k1Keypair.import(LABEL_KEY)).did()
    const data = await client.getDocumentData(DID)
    if (data.verificationMethods.atproto_label === labelKey && data.services.atproto_labeler?.endpoint === ENDPOINT) {
      console.error("DID document already declares the labeler")
      process.exit(0)
    }
    await client.updateData(DID, await Secp256k1Keypair.import(ROTATION_KEY), (op) => ({
      ...op,
      verificationMethods: { ...op.verificationMethods, atproto_label: labelKey },
      services: { ...op.services, atproto_labeler: { type: "AtprotoLabeler", endpoint: ENDPOINT } },
    }))
    console.error("added #atproto_labeler and #atproto_label to the DID document")
  '

# The PDS caches DID documents (for an hour before it even refreshes in the background) and cached moderation.test's when it created
# it, before the labeler entries existed; it would refuse to proxy reports ("could not resolve proxy did service url"). Drop the entry.
"${COMPOSE[@]}" exec -T -w /app/packages/pds pds node -e '
  const db = new (require("better-sqlite3"))("/pds/did_cache.sqlite")
  db.prepare("delete from did_doc where did = ?").run(process.argv[1])
' "$did"

if [[ ${OZONE_SERVER_DID:-} != "$did" ]]; then
  set_env OZONE_SERVER_DID "$did"
  echo "wrote OZONE_SERVER_DID=$did" >&2
fi
# Recreates Ozone if its environment changed. A full `up`, never `up -d ozone`: the PDS reads docker/.env too, so a changed value
# recreates it, and only a full `up` recreates the relay and Jetstream in its new network namespace along with it.
"${COMPOSE[@]}" up -d --remove-orphans 2>/dev/null
waited=0
until [[ $(curl -s -o /dev/null -m 5 -w '%{http_code}' "$ozone/xrpc/_health") == 2* ]]; do
  ((waited >= 120)) && { echo "FAIL Ozone not healthy; see: docker compose -f docker/compose.yaml logs ozone" >&2; exit 1; }
  sleep 2
  waited=$((waited + 2))
done

# Adds a team member unless they're already on the team.
add_member() {
  local member=$1 role=$2 out
  if out=$(post "$ozone" tools.ozone.team.addMember -u "admin:$admin_password" \
    -d "{\"did\":\"$member\",\"role\":\"tools.ozone.team.defs#$role\"}" 2>&1); then
    echo "added $member to the Ozone team ($role)" >&2
  elif [[ $out != *MemberAlreadyExists* ]]; then
    echo "$out" >&2
    return 1
  fi
}
add_member "$mod_did" roleAdmin
add_member "$triage_did" roleTriage

echo "$did"
