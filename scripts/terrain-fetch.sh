#!/usr/bin/env bash
# Downloads the terrain elevation tiles the AppView samples segments against (segments/segment-elevation): Terrarium PNGs from the AWS Open
# Data terrain tiles (https://registry.opendata.aws/terrain-tiles/), at one zoom, covering a region preset's box or a box given in degrees,
# into <out>/<zoom>/<x>/<y>.png. Tiles already there are skipped, and each is written beside its target and moved into place only once
# complete, so it's safe to run again after a failure. Point the AppView at the folder with TERRAIN_TILES_DIR (and TERRAIN_TILES_ZOOM when
# not the default 12).
#
#   scripts/terrain-fetch.sh <region | west,south,east,north> [--zoom N] [--out DIR]
#
# A region is a preset in scripts/segment-regions/<region>.conf. The default output folder is build/terrain. Needs curl.
set -euo pipefail

usage() { echo "usage: scripts/terrain-fetch.sh <region | west,south,east,north> [--zoom N] [--out DIR]" >&2; exit 2; }
[[ $# -ge 1 ]] || usage
target="$1"
shift
zoom=12
cd "$(dirname "$0")/.."
out="build/terrain"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --zoom) zoom="${2:?}"; shift 2 ;;
    --out) out="${2:?}"; shift 2 ;;
    *) usage ;;
  esac
done
[[ "$zoom" =~ ^[0-9]+$ && "$zoom" -ge 1 && "$zoom" -le 15 ]] || { echo "--zoom is 1 to 15" >&2; exit 2; }
command -v curl >/dev/null || { echo "Install curl first" >&2; exit 1; }

if [[ "$target" == *,* ]]; then
  IFS=, read -r west south east north <<<"$target"
else
  preset="scripts/segment-regions/$target.conf"
  [[ -f "$preset" ]] || { echo "No preset for $target: add $preset, or give west,south,east,north" >&2; exit 2; }
  BBOX=''
  # shellcheck source=/dev/null
  source "$preset"
  read -r west south east north <<<"$BBOX"
fi
[[ -n "${north:-}" ]] || { echo "The box is west south east north" >&2; exit 2; }

# Web Mercator tile columns and rows covering the box (north is the smaller row).
tiles=$(awk -v w="$west" -v s="$south" -v e="$east" -v n="$north" -v z="$zoom" '
  function col(lng) { return int((lng + 180) / 360 * 2 ^ z) }
  function row(lat,  r, pi) { pi = atan2(0, -1); r = lat * pi / 180; return int((1 - log(sin(r) / cos(r) + 1 / cos(r)) / pi) / 2 * 2 ^ z) }
  BEGIN { print col(w), col(e), row(n), row(s) }')
read -r x0 x1 y0 y1 <<<"$tiles"
[[ -n "${y1:-}" ]] || { echo "Couldn't work out the tiles for $west,$south,$east,$north" >&2; exit 1; }
count=$(((x1 - x0 + 1) * (y1 - y0 + 1)))
echo "Fetching $count tiles at zoom $zoom into $out"

fetched=0
for ((x = x0; x <= x1; x++)); do
  mkdir -p "$out/$zoom/$x"
  for ((y = y0; y <= y1; y++)); do
    tile="$out/$zoom/$x/$y.png"
    [[ -s "$tile" ]] && continue
    curl -fsS --retry 3 -o "$tile.part" "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/$zoom/$x/$y.png"
    mv "$tile.part" "$tile"
    fetched=$((fetched + 1))
  done
done
echo "Fetched $fetched new tiles; $out holds $(find "$out/$zoom" -name '*.png' | wc -l | tr -d ' ') at zoom $zoom"
