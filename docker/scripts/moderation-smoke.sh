#!/usr/bin/env bash
# End to end for content-moderation on the local stack, through the same calls the apps and Ozone make. bob posts a Public activity and
# a Followers one, each with a photo; alice follows him. alice reports the Followers activity through her PDS to the labeler and takes the
# block offer (an app.bsky.graph.block in her repo, which the AppView honors like the app's graph.block), then unblocks. mod.test (an admin
# on the labeler's team) opens the reported activity through the AppView's moderation.getSubject, photo included. Then the labeler applies
# !hide and !takedown to bob's Public activity, and finally negates both, checking alice's timeline, her lookup, a signed-out lookup and
# the photo at each step. Exits non-zero at the first check that fails.
# Needs scripts/dev.sh's stack and labeler, and the AppView on :8081 with the labeler and its triage account configured.
# Usage: docker/scripts/moderation-smoke.sh
set -euo pipefail

cd "$(dirname "$0")/.."
[[ -f .env ]] || { echo "docker/.env missing: cp docker/.env.example docker/.env" >&2; exit 1; }
# shellcheck disable=SC1091
source .env

pds="http://localhost:2583"
appview="http://localhost:8081"
ozone="http://localhost:3010"
admin=(-u "admin:${OZONE_ADMIN_PASSWORD:-local-dev-ozone-admin}")
labeler=${OZONE_SERVER_DID:?run scripts/dev.sh first: OZONE_SERVER_DID is unset}
# A 1x1 JPEG.
photo_b64='/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA='

ok() { echo "ok   $*"; }
fail() {
  echo "FAIL $*" >&2
  exit 1
}

# Calls an XRPC method and prints the body; fails on a non-2xx. Args: base, method, GET|POST, curl args...
xrpc() {
  local base=$1 method=$2 verb=$3 out status
  shift 3
  out=$(curl -sS -w '\n%{http_code}' -X "$verb" "$@" "$base/xrpc/$method") || fail "$method: no response from $base"
  status=${out##*$'\n'}
  out=${out%$'\n'*}
  [[ $status == 2* ]] || fail "$method: HTTP $status ${out:0:300}"
  echo "$out"
}

# HTTP status of a GET. Args: url, curl args...
status() { curl -s -o /dev/null -w '%{http_code}' "${@:2}" "$1"; }

# Waits up to 60 s for a command to succeed. Args: what, command...
wait_for() {
  local what=$1
  shift
  for _ in $(seq 30); do
    "$@" && return 0
    sleep 2
  done
  fail "timed out waiting for $what"
}

# did_<name> and jwt_<name> (bash 3 on macOS has no associative arrays); read with did <name> and jwt <name>.
for name in alice bob mod; do
  session=$(xrpc "$pds" com.atproto.server.createSession POST -H 'Content-Type: application/json' \
    -d "{\"identifier\":\"$name.test\",\"password\":\"$TEST_ACCOUNT_PASSWORD\"}")
  eval "did_$name=$(jq -r .did <<<"$session")"
  eval "jwt_$name=$(jq -r .accessJwt <<<"$session")"
done
did() { local v="did_$1"; echo "${!v}"; }
jwt() { local v="jwt_$1"; echo "${!v}"; }
appview_did=$(curl -s "$appview/.well-known/did.json" | jq -r .id)
ok "signed in as alice, bob and mod; the AppView is $appview_did"

# Service auth from [name]'s PDS for [lxm] at the AppView, as the apps send it.
service_auth() {
  xrpc "$pds" com.atproto.server.getServiceAuth GET -H "Authorization: Bearer $(jwt "$1")" -G \
    --data-urlencode "aud=$appview_did" --data-urlencode "lxm=$2" | jq -r .token
}

# An AppView query as [name] (or signed out with "-"). Args: name, method, query string. Sets $last_body and $last_status; [q] prints
# the body for a pipeline instead.
query() {
  local name=$1 method=$2 params=$3 auth=() out
  [[ $name != - ]] && auth=(-H "Authorization: Bearer $(service_auth "$name" "$method")")
  out=$(curl -sS -w '\n%{http_code}' "${auth[@]}" "$appview/xrpc/$method?$params")
  last_status=${out##*$'\n'}
  last_body=${out%$'\n'*}
}
q() {
  query "$@"
  echo "$last_body"
}

put_repo() { # name, collection, rkey, record json
  xrpc "$pds" com.atproto.repo.putRecord POST -H "Authorization: Bearer $(jwt "$1")" -H 'Content-Type: application/json' \
    -d "{\"repo\":\"$(did "$1")\",\"collection\":\"$2\",\"rkey\":\"$3\",\"validate\":false,\"record\":$4}" >/dev/null
}

# --------------------------------------------------------------------------------------------- setup
put_repo alice app.activitytracker.graph.follow 3kmodfollow "{\"\$type\":\"app.activitytracker.graph.follow\",\"subject\":\"$(did bob)\",\"createdAt\":\"2026-10-21T08:00:00Z\"}"

# Two photos, one per activity and unique to this run (trailing bytes after the JPEG's end), so no other activity holds either: a CID's
# photo is served for any activity it's on.
upload() {
  { base64 -d <<<"$photo_b64"; printf "$1"; } | xrpc "$pds" com.atproto.repo.uploadBlob POST -H "Authorization: Bearer $(jwt bob)" \
    -H 'Content-Type: image/jpeg' --data-binary @- | jq -c .blob
}
stamp=$(date +%s)
public_blob=$(upload "public$stamp")
followers_blob=$(upload "followers$stamp")
cid=$(jq -r '.ref["$link"]' <<<"$public_blob")
run() { # title, blob -> an activity record with that photo
  echo "{\"\$type\":\"app.activitytracker.feed.activity\",\"sport\":\"run\",\"title\":\"$1\",\"startedAt\":\"2026-10-21T07:00:00Z\",
    \"elapsedTime\":1800,\"createdAt\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",
    \"media\":[{\"\$type\":\"app.activitytracker.feed.activity#image\",\"image\":$2}]}"
}
public_rkey="3kmodpub$stamp"
followers_rkey="3kmodfol$stamp"
public_uri="at://$(did bob)/app.activitytracker.feed.activity/$public_rkey"
followers_uri="at://$(did bob)/app.activitytracker.feed.activity/$followers_rkey"
put_repo bob app.activitytracker.feed.activity "$public_rkey" "$(run 'Moderation smoke (Public)' "$public_blob")"

followers_space="at://$(did bob)/space/app.activitytracker.space.athlete/followers"
created=$(curl -sS -X POST -H "Authorization: Bearer $(jwt bob)" -H 'Content-Type: application/json' "$pds/xrpc/com.atproto.space.createSpace" -d "{
  \"spaceType\":\"app.activitytracker.space.athlete\",\"skey\":\"followers\",
  \"policy\":{\"\$type\":\"com.atproto.managedspace.policy\",\"managingApp\":\"$appview_did#activitytracker_space\"}}")
if [[ $created == *SpaceAlreadyExists* ]]; then
  xrpc "$pds" com.atproto.space.updateSpace POST -H "Authorization: Bearer $(jwt bob)" -H 'Content-Type: application/json' -d "{
    \"space\":\"$followers_space\",
    \"policy\":{\"\$type\":\"com.atproto.managedspace.policy\",\"managingApp\":\"$appview_did#activitytracker_space\"}}" >/dev/null
fi
xrpc "$pds" com.atproto.space.putRecord POST -H "Authorization: Bearer $(jwt bob)" -H 'Content-Type: application/json' -d "{
  \"space\":\"$followers_space\",\"repo\":\"$(did bob)\",\"collection\":\"app.activitytracker.feed.activity\",
  \"rkey\":\"$followers_rkey\",\"record\":$(run 'Moderation smoke (Followers)' "$followers_blob")}" >/dev/null
xrpc "$appview" app.activitytracker.space.notifySpace POST -H "Authorization: Bearer $(service_auth bob app.activitytracker.space.notifySpace)" \
  -H 'Content-Type: application/json' -d "{\"space\":\"$followers_space\"}" >/dev/null
ok "bob posted a Public and a Followers activity, each with a photo; alice follows bob"

lookup_status() { query "$1" app.activitytracker.feed.getActivity "uri=$(jq -rn --arg u "$2" '$u|@uri')"; echo "$last_status"; }
in_timeline() { q alice app.activitytracker.feed.getTimeline "limit=100" | jq -e --arg u "$1" '[.activities[].uri] | index($u) != null' >/dev/null; }
indexed() { [[ $(lookup_status alice "$1") == 200 ]]; }
wait_for "the Public activity" indexed "$public_uri"
wait_for "the Followers activity (synced from bob's space)" indexed "$followers_uri"
ok "alice sees both; signed out, the Followers one answers $(lookup_status - "$followers_uri")"

# --------------------------------------------------------------------------------------------- report and block
followers_cid=$(q alice app.activitytracker.feed.getActivity "uri=$(jq -rn --arg u "$followers_uri" '$u|@uri')" | jq -r .activity.cid)
report=$(xrpc "$pds" com.atproto.moderation.createReport POST -H "Authorization: Bearer $(jwt alice)" -H 'Content-Type: application/json' \
  -H "atproto-proxy: $labeler#atproto_labeler" -d "{\"reasonType\":\"com.atproto.moderation.defs#reasonRude\",
  \"subject\":{\"\$type\":\"com.atproto.repo.strongRef\",\"uri\":\"$followers_uri\",\"cid\":\"$followers_cid\"}}")
review=$(xrpc "$ozone" tools.ozone.moderation.queryStatuses GET "${admin[@]}" -G --data-urlencode "subject=$followers_uri" | jq -r '.subjectStatuses[0].reviewState')
[[ $review == *reviewOpen ]] || fail "the report isn't open in Ozone: $review"
ok "alice reported it (report $(jq -r .id <<<"$report"), reportedBy $(jq -r .reportedBy <<<"$report")); Ozone has it $review"

put_repo alice app.bsky.graph.block 3kmodblock "{\"\$type\":\"app.bsky.graph.block\",\"subject\":\"$(did bob)\",\"createdAt\":\"2026-10-21T08:01:00Z\"}"
hidden_from_alice() { [[ $(lookup_status alice "$public_uri") != 200 ]]; }
wait_for "alice's block to apply" hidden_from_alice
ok "alice took the block offer: bob's activities answer $(lookup_status alice "$public_uri") for her"
xrpc "$pds" com.atproto.repo.deleteRecord POST -H "Authorization: Bearer $(jwt alice)" -H 'Content-Type: application/json' \
  -d "{\"repo\":\"$(did alice)\",\"collection\":\"app.bsky.graph.block\",\"rkey\":\"3kmodblock\"}" >/dev/null
wait_for "alice's unblock" indexed "$public_uri"
ok "unblocked again, for the label checks"

# --------------------------------------------------------------------------------------------- the moderator
query mod app.activitytracker.moderation.getSubject "uri=$(jq -rn --arg u "$followers_uri" '$u|@uri')"
subject=$last_body
[[ $last_status == 200 ]] || fail "getSubject as mod.test: $last_status $subject"
photo=$(jq -r '.activity.media[0].fullsize' <<<"$subject")
[[ $photo == *"viewer=$(jq -rn --arg u "$(did mod)" '$u|@uri')"* ]] || fail "the photo isn't signed for mod.test: $photo"
local_photo="$appview/${photo#*://*/}"
[[ $(status "$local_photo") == 200 ]] || fail "mod.test's photo link: $(status "$local_photo")"
audit=$(docker compose -f compose.yaml exec -T appview-db psql -U appview -d "${APPVIEW_DB:-appview}" -tAc \
  "select count(*) from moderation_access where subject_uri = '$followers_uri' and moderator_did = '$(did mod)'" 2>/dev/null || echo "?")
ok "mod.test opened it ($(jq -r .visibility <<<"$subject")) and its photo; audit entries: $audit"
query alice app.activitytracker.moderation.getSubject "uri=$(jq -rn --arg u "$followers_uri" '$u|@uri')"
[[ $last_status == 403 ]] || fail "alice isn't a moderator but getSubject answered $last_status"
ok "alice, not on the team, is refused ($last_status)"

# --------------------------------------------------------------------------------------------- labels
label() { # value, negate (true|false)
  local create="[\"$1\"]" negate="[]"
  [[ $2 == true ]] && { negate=$create; create="[]"; }
  xrpc "$ozone" tools.ozone.moderation.emitEvent POST "${admin[@]}" -H 'Content-Type: application/json' -d "{
    \"event\":{\"\$type\":\"tools.ozone.moderation.defs#modEventLabel\",\"createLabelVals\":$create,\"negateLabelVals\":$negate},
    \"subject\":{\"\$type\":\"com.atproto.repo.strongRef\",\"uri\":\"$public_uri\",\"cid\":\"$(q bob app.activitytracker.feed.getActivity \
      "uri=$(jq -rn --arg u "$public_uri" '$u|@uri')" | jq -r .activity.cid)\"},\"createdBy\":\"$labeler\"}" >/dev/null
}
public_photo="$appview/img/media/$(did bob)/$cid"
not_in_timeline() { ! in_timeline "$public_uri"; }

label '!hide' false
wait_for "!hide to apply" not_in_timeline
[[ $(lookup_status alice "$public_uri") == 200 && $(lookup_status - "$public_uri") == 200 ]] || fail "a hidden activity should still open by URI"
labels=$(q alice app.activitytracker.feed.getActivity "uri=$(jq -rn --arg u "$public_uri" '$u|@uri')" | jq -c '[.activity.labels[]?.val]')
[[ $(status "$public_photo") == 302 ]] || fail "a hidden activity's photo should still redirect"
ok "!hide: out of alice's timeline, still opens by URI for alice and signed out (labels $labels), photo redirects"

label '!takedown' false
gone() { [[ $(lookup_status alice "$public_uri") != 200 ]]; }
wait_for "!takedown to apply" gone
[[ $(lookup_status - "$public_uri") != 200 ]] || fail "a taken-down activity should be gone signed out"
[[ $(lookup_status bob "$public_uri") == 200 ]] || fail "bob should still see his own"
[[ $(status "$public_photo") == 404 ]] || fail "a taken-down activity's photo answered $(status "$public_photo")"
ok "!takedown: not found for alice ($(lookup_status alice "$public_uri")) and signed out; bob still sees it; photo 404"

label '!takedown' true
label '!hide' true
wait_for "the negations to apply" in_timeline "$public_uri"
[[ $(lookup_status - "$public_uri") == 200 && $(status "$public_photo") == 302 ]] || fail "negated: should be served again"
ok "negated: back in alice's timeline, opens signed out, photo redirects"
echo "PASS"
