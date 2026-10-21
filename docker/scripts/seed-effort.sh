#!/usr/bin/env bash
# Seeds a matchable activity for <name>.test without the app, for testing segment efforts and leaderboards locally
# (segments/segment-track, segments/segment-leaderboards). As the athlete it:
#   - ensures their `self` space (managed by the AppView, as the app makes it) and announces it to the AppView (notifySpace);
#   - turns segment matching on in actor.segmentPrefs, with --hide hiding them from leaderboards;
#   - writes a public feed.activity in their repo, on --date at 07:00 UTC;
#   - writes its feed.segmentTrack in `self`: the GPX's track points, timed as if covered at --speed m/s, with --exclude marking it
#     excluded from leaderboards.
# The AppView syncs `self` and matches the track against the segments it passes (see seed-segment.sh), once it has a signing key.
# Usage: docker/scripts/seed-effort.sh alice track.gpx [--speed 5] [--sport run] [--sub-sport eMountain] [--date 2026-10-20]
#        [--hide] [--exclude] [--pds http://localhost:2583] [--appview http://localhost:8081]
set -euo pipefail

gpx_path="$(cd "$(dirname "${2:?usage: seed-effort.sh <name> <gpx> [options]}")" && pwd)/$(basename "$2")"
cd "$(dirname "$0")/.."
[[ -f .env ]] || { echo "docker/.env missing: cp docker/.env.example docker/.env" >&2; exit 1; }
# shellcheck disable=SC1091
source .env

name=$1
shift 2
TEST_ACCOUNT_PASSWORD="$TEST_ACCOUNT_PASSWORD" python3 -I - "$name" "$gpx_path" "$@" <<'PY'
import argparse, json, math, os, random, sys, time, urllib.error, urllib.parse, urllib.request
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(prog="seed-effort.sh")
parser.add_argument("name")
parser.add_argument("gpx")
parser.add_argument("--speed", type=float, default=5.0)
parser.add_argument("--sport", default="run")
parser.add_argument("--sub-sport")
parser.add_argument("--date", default=time.strftime("%Y-%m-%d", time.gmtime()))
parser.add_argument("--hide", action="store_true")
parser.add_argument("--exclude", action="store_true")
parser.add_argument("--pds", default="http://localhost:2583")
parser.add_argument("--appview", default="http://localhost:8081")
args = parser.parse_args()

NS = "app.activitytracker"
SPACE_TYPE = f"{NS}.space.athlete"


def fail(msg):
    sys.exit(f"FAIL {msg}")


def call(base, method, verb="GET", params=None, body=None, raw=None, token=None, content_type="application/json"):
    url = f"{base}/xrpc/{method}" + ("?" + urllib.parse.urlencode(params) if params else "")
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    data = json.dumps(body).encode() if body is not None else raw
    if data is not None:
        headers["Content-Type"] = content_type
    try:
        with urllib.request.urlopen(urllib.request.Request(url, data=data, headers=headers, method=verb), timeout=30) as resp:
            return resp.status, json.loads(resp.read() or b"{}")
    except urllib.error.HTTPError as e:
        payload = e.read()
        try:
            return e.code, json.loads(payload)
        except ValueError:
            return e.code, {"raw": payload.decode(errors="replace")}


def must(result, what):
    status, payload = result
    if not 200 <= status < 300:
        fail(f"{what}: HTTP {status} {str(payload)[:300]}")
    return payload


def tid():
    alphabet = "234567abcdefghijklmnopqrstuvwxyz"
    value = (int(time.time() * 1_000_000) << 10) | random.randrange(1024)
    return "".join(alphabet[(value >> (5 * i)) & 31] for i in reversed(range(13)))


def meters(a, b):
    lat1, lat2 = math.radians(a[0]), math.radians(b[0])
    dlat, dlng = lat2 - lat1, math.radians(b[1] - a[1])
    h = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlng / 2) ** 2
    return 2 * 6_371_000 * math.asin(math.sqrt(h))


points = [(float(p.get("lat")), float(p.get("lon"))) for p in ET.parse(args.gpx).iter() if p.tag.rsplit("}", 1)[-1] == "trkpt"]
if len(points) < 2:
    fail(f"only {len(points)} track points")

session = must(
    call(args.pds, "com.atproto.server.createSession", "POST",
         body={"identifier": f"{args.name}.test", "password": os.environ["TEST_ACCOUNT_PASSWORD"]}),
    f"createSession as {args.name}.test",
)
did, token = session["did"], session["accessJwt"]
self_space = f"at://{did}/space/{SPACE_TYPE}/self"

# The AppView's service DID, as the app resolves it: the managing app of the space, and the audience of notifySpace.
with urllib.request.urlopen(f"{args.appview}/.well-known/did.json", timeout=10) as resp:
    appview_did = json.loads(resp.read())["id"]

read_policy = {"$type": "com.atproto.simplespace.defs#managingAppPolicy", "managingApp": f"{appview_did}#activitytracker_space"}
status, payload = call(args.pds, "com.atproto.simplespace.createSpace", "POST", token=token, body={
    "spaceType": SPACE_TYPE, "skey": "self", "readPolicy": read_policy,
    "writePolicy": {"$type": "com.atproto.simplespace.defs#memberListPolicy"},
    "appAccess": {"$type": "com.atproto.simplespace.defs#open"},
})
if payload.get("error") == "SpaceAlreadyExists":
    # A tunnel restart changes the AppView's DID: point an existing space at the current one, as the app does.
    must(call(args.pds, "com.atproto.simplespace.updateSpace", "POST", token=token,
              body={"space": self_space, "readPolicy": read_policy}), "updateSpace")
elif not 200 <= status < 300:
    fail(f"createSpace: HTTP {status} {payload}")

notify = f"{NS}.space.notifySpace"
service = must(call(args.pds, "com.atproto.server.getServiceAuth", params={"aud": appview_did, "lxm": notify}, token=token), "getServiceAuth")
must(call(args.appview, notify, "POST", body={"space": self_space}, token=service["token"]), "notifySpace")


def put_space(collection, rkey, record):
    must(call(args.pds, "com.atproto.space.putRecord", "POST", token=token, body={
        "space": self_space, "repo": did, "collection": collection, "rkey": rkey,
        "record": {"$type": collection, **record}, "validate": False,
    }), f"space putRecord {collection}")


now = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
prefs = {"matching": True, "updatedAt": now}
if args.hide:
    prefs["hideFromLeaderboards"] = True
put_space(f"{NS}.actor.segmentPrefs", "self", prefs)

# The track timed at a steady speed: seconds from its first point.
distance = [0.0]
for a, b in zip(points, points[1:]):
    distance.append(distance[-1] + meters(a, b))
elapsed = max(1, round(distance[-1] / args.speed))
rkey = tid()
activity_uri = f"at://{did}/{NS}.feed.activity/{rkey}"
activity = {
    "$type": f"{NS}.feed.activity", "sport": args.sport, "startedAt": f"{args.date}T07:00:00Z", "utcOffset": 0,
    "elapsedTime": elapsed, "distance": round(distance[-1]), "createdAt": now,
}
if args.sub_sport:
    activity["subSport"] = args.sub_sport
must(call(args.pds, "com.atproto.repo.putRecord", "POST", token=token, body={
    "repo": did, "collection": f"{NS}.feed.activity", "rkey": rkey, "record": activity, "validate": False,
}), "putRecord feed.activity")

data = {"pieces": [{
    "lat": [round(lat * 1e7) for lat, _ in points],
    "lng": [round(lng * 1e7) for _, lng in points],
    "t": [round(d / args.speed) for d in distance],
}]}
blob = must(call(args.pds, "com.atproto.repo.uploadBlob", "POST", raw=json.dumps(data).encode(), token=token), "uploadBlob")["blob"]
track = {"activity": activity_uri, "data": blob, "createdAt": now}
if args.exclude:
    track["excludeFromLeaderboards"] = True
put_space(f"{NS}.feed.segmentTrack", rkey, track)
print(f"ok   {activity_uri} ({distance[-1]:.0f} m in {elapsed} s)")
PY
