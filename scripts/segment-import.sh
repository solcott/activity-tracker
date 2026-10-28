#!/usr/bin/env bash
# One import run for a region (segments/segment-import): extract its candidate segments from an OpenStreetMap extract and any agency trail
# datasets for it (GeoJSON text sequences, see server/segment-importer/README.md), stage them in the AppView, publish the ridden ones as
# the importer's service account, then refine the segments already published with the candidates' finer lines. Each step must succeed before the next runs, and every step is safe to repeat, so a scheduler can
# simply run this again.
#
#   scripts/segment-import.sh <region> [file.osm.pbf] [--agency usfs|nps=<file.geojsonl>]...
#
# The service account comes from SEGMENT_IMPORTER_IDENTIFIER, SEGMENT_IMPORTER_PASSWORD and SEGMENT_IMPORTER_PDS_URL (and APPVIEW_URL,
# APPVIEW_DID for an AppView other than the local one), in the environment or docker/.env. The AppView's SEGMENT_IMPORTER_DID must name
# the same account.
set -euo pipefail

usage() {
  echo "usage: scripts/segment-import.sh <region> [file.osm.pbf] [--agency usfs|nps=<file.geojsonl>]..." >&2
  echo "  region: lower-case letters, digits and dashes, such as us-vermont; at least one extract or agency dataset" >&2
  exit 2
}
[[ $# -ge 2 ]] || usage
region="$1"
shift
cd "$(dirname "$0")/.."

inputs=()
while [[ $# -gt 0 ]]; do
  if [[ $1 == --agency ]]; then
    [[ $# -ge 2 && $2 == *=* ]] || usage
    file="${2#*=}"
    [[ -f "$file" ]] || { echo "No such agency dataset: $file" >&2; exit 2; }
    inputs+=(--agency "$2")
    shift 2
  else
    [[ -f "$1" ]] || { echo "No such extract: $1" >&2; exit 2; }
    inputs+=("$1")
    shift
  fi
done

out="build/segment-import/$region.ndjson"
mkdir -p "$(dirname "$out")"
importer() { ./gradlew -q :server-segment-importer:run --args="$*"; }

echo "==> extract $region"
importer extract "${inputs[@]}" --region "$region" --out "$out"
echo "==> stage $out"
importer stage "$out"
echo "==> publish $region"
importer publish --region "$region"
echo "==> refine $region"
importer refine "$out"
