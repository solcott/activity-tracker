#!/usr/bin/env bash
# One import run for a region (segments/segment-import): extract its candidate segments from an OpenStreetMap extract and any agency trail
# datasets for it (GeoJSON text sequences, see server/segment-importer/README.md), stage them in the AppView, publish the ridden ones as
# the importer's service account, then refine the segments already published with the candidates' finer lines and names. Each step must
# succeed before the next runs, and every step is safe to repeat, so a scheduler can simply run this again.
#
#   scripts/segment-import.sh <region> [--all] [--sites]
#   scripts/segment-import.sh <region> [file.osm.pbf] [--agency usfs|nps|blm=<file.geojsonl>]... [--lang <code>] [--all] [--sites]
#
# With no inputs, the region's preset (scripts/segment-regions/<region>.conf) names them, and scripts/segment-fetch.sh downloads them first.
# --all publishes every staged candidate that duplicates no live segment, ridden or not. --sites then also extracts the region's dive sites
# from its whole OSM extract (a preset's download, not the trails-only cut) and publishes those that duplicate no live site
# (dive-sites/site-import).
#
# The service account comes from SEGMENT_IMPORTER_IDENTIFIER, SEGMENT_IMPORTER_PASSWORD and SEGMENT_IMPORTER_PDS_URL (and APPVIEW_URL,
# APPVIEW_DID for an AppView other than the local one, whose DID the importer derives, device mode included), in the environment or
# docker/.env. The AppView's SEGMENT_IMPORTER_DID must name the same account.
set -euo pipefail

usage() {
  echo "usage: scripts/segment-import.sh <region> [file.osm.pbf] [--agency usfs|nps|blm=<file.geojsonl>]... [--lang <code>] [--all] [--sites]" >&2
  echo "  region: lower-case letters, digits and dashes, such as us-utah; with no inputs, its preset in scripts/segment-regions names them" >&2
  echo "  lang: the region's two-letter language code, en unless given (or the preset's)" >&2
  echo "  --all: publish every staged candidate, ridden or not" >&2
  echo "  --sites: also publish the region's OSM dive sites, from the whole extract" >&2
  exit 2
}
[[ $# -ge 1 ]] || usage
region="$1"
shift
cd "$(dirname "$0")/.."

inputs=()
lang=
all=false
sites=false
pbf=
while [[ $# -gt 0 ]]; do
  if [[ $1 == --all ]]; then
    all=true
    shift
  elif [[ $1 == --sites ]]; then
    sites=true
    shift
  elif [[ $1 == --lang ]]; then
    [[ $# -ge 2 ]] || usage
    lang="$2"
    shift 2
  elif [[ $1 == --agency ]]; then
    [[ $# -ge 2 && $2 == *=* ]] || usage
    file="${2#*=}"
    [[ -f "$file" ]] || { echo "No such agency dataset: $file" >&2; exit 2; }
    inputs+=(--agency "$2")
    shift 2
  else
    [[ -f "$1" ]] || { echo "No such extract: $1" >&2; exit 2; }
    inputs+=("$1")
    pbf="$1"
    shift
  fi
done

if [[ ${#inputs[@]} -eq 0 ]]; then
  preset="scripts/segment-regions/$region.conf"
  [[ -f "$preset" ]] || { echo "No inputs and no preset for $region: give its files, or add $preset" >&2; exit 2; }
  scripts/segment-fetch.sh "$region"
  AGENCIES='' REGION_LANG=''
  # shellcheck source=/dev/null
  source "$preset"
  fetched="build/segment-import/$region"
  inputs+=("$fetched/$region-trails.osm.pbf")
  pbf="$fetched/$(basename "$GEOFABRIK")-latest.osm.pbf"
  for kind in $AGENCIES; do inputs+=(--agency "$kind=$fetched/$kind.geojsonl"); done
  [[ -n "$lang" ]] || lang="${REGION_LANG:-en}"
fi
[[ -n "$lang" ]] || lang=en
if $sites && [[ -z "$pbf" ]]; then
  echo "--sites needs an OSM extract" >&2
  exit 2
fi

out="build/segment-import/$region.ndjson"
mkdir -p "$(dirname "$out")"
importer() { ./gradlew -q :server-segment-importer:run --args="$*"; }

echo "==> extract $region"
importer extract "${inputs[@]}" --region "$region" --lang "$lang" --out "$out"
echo "==> stage $out"
importer stage "$out"
if $all; then
  echo "==> publish $region (every candidate)"
  importer publish --region "$region" --all "$out"
else
  echo "==> publish $region"
  importer publish --region "$region"
fi
echo "==> refine $region"
importer refine "$out"
if $sites; then
  sites_out="build/segment-import/$region-sites.ndjson"
  echo "==> extract-sites $region"
  importer extract-sites "$pbf" --region "$region" --out "$sites_out"
  echo "==> publish-sites $sites_out"
  importer publish-sites "$sites_out"
fi
