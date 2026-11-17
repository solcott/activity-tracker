#!/usr/bin/env bash
# One import run for a region (segments/segment-import): extract its candidate segments from an OpenStreetMap extract and any agency trail
# datasets for it (GeoJSON text sequences, see server/segment-importer/README.md), stage them in the AppView, publish the ridden ones as
# the importer's service account, refine the segments already published with the candidates' finer lines and names, then publish the
# trails and named roads those segments, and athletes' segments there, lie on (segments/path-import). Each step must
# succeed before the next runs, and every step is safe to repeat, so a scheduler can simply run this again.
#
#   scripts/segment-import.sh <region> [--all] [--sites | --sites-only] [--handoff]
#   scripts/segment-import.sh <region> [file.osm.pbf] [--agency usfs|nps|blm=<file.geojsonl>]... [--lang <code>] [--all] [--sites] [--handoff]
#
# With no inputs, the region's preset (scripts/segment-regions/<region>.conf) names them, and scripts/segment-fetch.sh downloads them first.
# --all publishes every staged candidate that duplicates no live segment, ridden or not. --sites then also extracts the region's dive sites
# from its whole OSM extract (a preset's download, not the trails-only cut) and publishes those that duplicate no live site
# (dive-sites/site-import); a preset's SITES_BBOX first cuts that extract to its box (eg-red-sea: Egypt's Red Sea coast, not its
# Mediterranean one). --sites-only does just that, skipping the segment steps. --handoff finally republishes the segments and dive sites athletes deleted or left behind while others still
# used them, whatever their region (segments/segment-handoffs).
#
# The service account comes from SEGMENT_IMPORTER_IDENTIFIER, SEGMENT_IMPORTER_PASSWORD and SEGMENT_IMPORTER_PDS_URL (and APPVIEW_URL,
# APPVIEW_DID for an AppView other than the local one, whose DID the importer derives, device mode included), in the environment or
# docker/.env. The AppView's SEGMENT_IMPORTER_DID must name the same account.
#
# When build/terrain holds terrain tiles (scripts/terrain-fetch.sh), extract names two-way trails by their climb, and refine renames those
# already published.
set -euo pipefail

usage() {
  echo "usage: scripts/segment-import.sh <region> [file.osm.pbf] [--agency usfs|nps|blm=<file.geojsonl>]... [--lang <code>] [--all] [--sites | --sites-only] [--handoff]" >&2
  echo "  region: lower-case letters, digits and dashes, such as us-utah; with no inputs, its preset in scripts/segment-regions names them" >&2
  echo "  lang: the region's two-letter language code, en unless given (or the preset's)" >&2
  echo "  --all: publish every staged candidate, ridden or not" >&2
  echo "  --sites: also publish the region's OSM dive sites, from the whole extract (cut to the preset's SITES_BBOX, if it sets one)" >&2
  echo "  --sites-only: publish only the dive sites, skipping the segment steps" >&2
  echo "  --handoff: also republish handed-off segments and dive sites, of every region" >&2
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
segments=true
handoff=false
sites_bbox=
pbf=
while [[ $# -gt 0 ]]; do
  if [[ $1 == --all ]]; then
    all=true
    shift
  elif [[ $1 == --sites ]]; then
    sites=true
    shift
  elif [[ $1 == --sites-only ]]; then
    sites=true
    segments=false
    shift
  elif [[ $1 == --handoff ]]; then
    handoff=true
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
  AGENCIES='' REGION_LANG='' SITES_BBOX=''
  # shellcheck source=/dev/null
  source "$preset"
  fetched="build/segment-import/$region"
  inputs+=("$fetched/$region-trails.osm.pbf")
  pbf="$fetched/$(basename "$GEOFABRIK")-latest.osm.pbf"
  for kind in $AGENCIES; do inputs+=(--agency "$kind=$fetched/$kind.geojsonl"); done
  [[ -n "$lang" ]] || lang="${REGION_LANG:-en}"
  sites_bbox="$SITES_BBOX"
fi
[[ -n "$lang" ]] || lang=en
if $sites && [[ -z "$pbf" ]]; then
  echo "--sites needs an OSM extract" >&2
  exit 2
fi

out="build/segment-import/$region.ndjson"
paths_out="build/segment-import/$region-paths.ndjson"
mkdir -p "$(dirname "$out")"
importer() { ./gradlew -q :server-segment-importer:run --args="$*"; }

if $segments; then
  echo "==> extract $region"
  # Terrain tiles scripts/terrain-fetch.sh writes name two-way trails that clearly climb "(uphill)" and "(downhill)"
  # (segments/segment-direction); TERRAIN_TILES_ZOOM, as the AppView reads it, when they aren't at zoom 12.
  terrain=()
  if [[ -d build/terrain/${TERRAIN_TILES_ZOOM:-12} ]]; then
    terrain=(--terrain build/terrain --terrain-zoom "${TERRAIN_TILES_ZOOM:-12}")
  fi
  importer extract "${inputs[@]}" --region "$region" --lang "$lang" --out "$out" --paths "$paths_out" ${terrain[@]+"${terrain[@]}"}
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
  echo "==> publish-paths $region"
  importer publish-paths "$paths_out"
fi
if $sites; then
  sites_out="build/segment-import/$region-sites.ndjson"
  if [[ -n "$sites_bbox" ]]; then
    command -v osmium >/dev/null || { echo "Install first: osmium (brew install osmium-tool)" >&2; exit 1; }
    read -r west south east north <<<"$sites_bbox"
    [[ -n "$north" ]] || { echo "SITES_BBOX is west south east north" >&2; exit 2; }
    clipped="build/segment-import/$region-sites.osm.pbf"
    echo "==> clip $pbf to $sites_bbox"
    osmium extract -b "$west,$south,$east,$north" "$pbf" -O -o "$clipped.part.osm.pbf"
    mv "$clipped.part.osm.pbf" "$clipped"
    pbf="$clipped"
  fi
  echo "==> extract-sites $region"
  importer extract-sites "$pbf" --region "$region" --out "$sites_out"
  echo "==> publish-sites $sites_out"
  importer publish-sites "$sites_out"
fi
if $handoff; then
  echo "==> handoff"
  importer handoff
fi
