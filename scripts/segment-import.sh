#!/usr/bin/env bash
# One import run for a region (segments/segment-import): extract its candidate segments from an OpenStreetMap extract, stage them in
# the AppView, then publish the ridden ones as the importer's service account. Each step must succeed before the next runs, and every
# step is safe to repeat, so a scheduler can simply run this again.
#
#   scripts/segment-import.sh <region> <file.osm.pbf>
#
# The service account comes from SEGMENT_IMPORTER_IDENTIFIER, SEGMENT_IMPORTER_PASSWORD and SEGMENT_IMPORTER_PDS_URL (and APPVIEW_URL,
# APPVIEW_DID for an AppView other than the local one), in the environment or docker/.env. The AppView's SEGMENT_IMPORTER_DID must name
# the same account.
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: scripts/segment-import.sh <region> <file.osm.pbf>" >&2
  echo "  region: lower-case letters, digits and dashes, such as us-vermont" >&2
  exit 2
fi
region="$1"
pbf="$2"
cd "$(dirname "$0")/.."
[[ -f "$pbf" ]] || { echo "No such extract: $pbf" >&2; exit 2; }

out="build/segment-import/$region.ndjson"
mkdir -p "$(dirname "$out")"
importer() { ./gradlew -q :server-segment-importer:run --args="$*"; }

echo "==> extract $region"
importer extract "$pbf" --region "$region" --out "$out"
echo "==> stage $out"
importer stage "$out"
echo "==> publish $region"
importer publish --region "$region"
