#!/usr/bin/env bash
# Writes a feed.segment to <name>.test's repo from a GPX file, for local testing until segments can be created in the app
# (segments/segment-records). The line is the GPX's track points from startIndex to endIndex (inclusive; the whole track by default),
# encoded as a precision-6 polyline, with source "athlete" and licence CC0-1.0. The AppView indexes it from Jetstream: check with
# app.activitytracker.feed.getSegment on the printed URI. Each run writes a new segment.
# Usage: docker/scripts/seed-segment.sh alice "Hill climb" run track.gpx [startIndex endIndex] [pds-url]
set -euo pipefail

gpx_path="$(cd "$(dirname "${4:?usage: seed-segment.sh <name> <segment-name> <sport> <gpx> [start end] [pds-url]}")" && pwd)/$(basename "$4")"
cd "$(dirname "$0")/.."
[[ -f .env ]] || { echo "docker/.env missing: cp docker/.env.example docker/.env" >&2; exit 1; }
# shellcheck disable=SC1091
source .env

name=$1
segment_name=$2
sport=$3
start=${5:-0}
end=${6:--1}
pds="${7:-http://localhost:2583}"
case $sport in ride | run | hike | ski | paddle) ;; *) echo "sport must be ride, run, hike, ski or paddle" >&2; exit 1 ;; esac

json_field() { sed -nE "s/.*\"$1\":\"([^\"]+)\".*/\1/p"; }

session=$(curl -sSf -X POST "$pds/xrpc/com.atproto.server.createSession" -H 'Content-Type: application/json' \
  -d "{\"identifier\":\"$name.test\",\"password\":\"$TEST_ACCOUNT_PASSWORD\"}") || { echo "FAIL createSession as $name.test" >&2; exit 1; }
did=$(json_field did <<<"$session")
token=$(json_field accessJwt <<<"$session")

# The putRecord body: the GPX's points cut to [start, end] and encoded at precision 6, under a fresh TID.
body=$(python3 -I - "$gpx_path" "$start" "$end" "$did" "$segment_name" "$sport" <<'PY'
import json, sys, time, random
import xml.etree.ElementTree as ET

path, start, end, did, name, sport = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), sys.argv[4], sys.argv[5], sys.argv[6]
points = [(float(p.get("lat")), float(p.get("lon"))) for p in ET.parse(path).iter() if p.tag.rsplit("}", 1)[-1] == "trkpt"]
points = points[start:] if end < 0 else points[start:end + 1]
if len(points) < 2:
    sys.exit(f"only {len(points)} track points in range")

def encode(value):
    value = ~(value << 1) if value < 0 else value << 1
    out = ""
    while value >= 0x20:
        out += chr((0x20 | (value & 0x1F)) + 63)
        value >>= 5
    return out + chr(value + 63)

line, last_lat, last_lng = "", 0, 0
for lat, lng in points:
    lat, lng = round(lat * 1e6), round(lng * 1e6)
    line += encode(lat - last_lat) + encode(lng - last_lng)
    last_lat, last_lng = lat, lng

alphabet = "234567abcdefghijklmnopqrstuvwxyz"
tid_value = (int(time.time() * 1_000_000) << 10) | random.randrange(1024)
tid = "".join(alphabet[(tid_value >> (5 * i)) & 31] for i in reversed(range(13)))
record = {
    "$type": "app.activitytracker.feed.segment",
    "name": name,
    "sport": sport,
    "line": line,
    "source": "athlete",
    "license": "CC0-1.0",
    "createdAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
}
print(json.dumps({"repo": did, "collection": "app.activitytracker.feed.segment", "rkey": tid, "record": record}))
PY
)

out=$(curl -sS -w '\n%{http_code}' -X POST "$pds/xrpc/com.atproto.repo.putRecord" -H "Authorization: Bearer $token" \
  -H 'Content-Type: application/json' -d "$body")
status=${out##*$'\n'}
out=${out%$'\n'*}
[[ $status == 2* ]] || { echo "FAIL putRecord: HTTP $status ${out:0:300}" >&2; exit 1; }
echo "ok   $(json_field uri <<<"$out")"
