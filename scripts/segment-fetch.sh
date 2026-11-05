#!/usr/bin/env bash
# Downloads a region's segment import inputs (segments/segment-import) from its preset, scripts/segment-regions/<region>.conf, into
# build/segment-import/<region>/: its Geofabrik OpenStreetMap extract (only when Geofabrik's is newer), cut down to the features the importer
# reads (<region>-trails.osm.pbf), and each preset agency's trails touching the region's box as a GeoJSON text sequence (<kind>.geojsonl).
# Every file is written beside its target and moved into place only once complete, so a failed download never leaves a partial input.
#
#   scripts/segment-fetch.sh <region>
#
# Needs curl, osmium (brew install osmium-tool) and ogr2ogr (brew install gdal). scripts/segment-import.sh <region> runs this first.
set -euo pipefail

[[ $# -eq 1 ]] || { echo "usage: scripts/segment-fetch.sh <region>" >&2; exit 2; }
region="$1"
cd "$(dirname "$0")/.."

preset="scripts/segment-regions/$region.conf"
[[ -f "$preset" ]] || { echo "No preset for $region: add $preset (see scripts/segment-regions/us-utah.conf)" >&2; exit 2; }
GEOFABRIK='' BBOX='' AGENCIES=''
# shellcheck source=/dev/null
source "$preset"
[[ -n "$GEOFABRIK" && -n "$BBOX" ]] || { echo "$preset must set GEOFABRIK and BBOX" >&2; exit 2; }
read -r west south east north <<<"$BBOX"
[[ -n "$north" ]] || { echo "$preset: BBOX is west south east north" >&2; exit 2; }

missing=()
command -v curl >/dev/null || missing+=("curl")
command -v osmium >/dev/null || missing+=("osmium (brew install osmium-tool)")
[[ -z "$AGENCIES" ]] || command -v ogr2ogr >/dev/null || missing+=("ogr2ogr (brew install gdal)")
if [[ ${#missing[@]} -gt 0 ]]; then
  printf 'Install first: %s\n' "${missing[@]}" >&2
  exit 1
fi

dir="build/segment-import/$region"
mkdir -p "$dir"

size() { du -h "$1" | cut -f1; }

# The extract: curl -z asks only for one newer than ours, and leaves the .part empty when it isn't.
extract="$dir/$(basename "$GEOFABRIK")-latest.osm.pbf"
echo "==> OpenStreetMap: https://download.geofabrik.de/$GEOFABRIK-latest.osm.pbf"
rm -f "$extract.part"
newer=()
[[ -f "$extract" ]] && newer=(-z "$extract")
curl -fsSL --retry 3 "${newer[@]+"${newer[@]}"}" -o "$extract.part" "https://download.geofabrik.de/$GEOFABRIK-latest.osm.pbf"
if [[ -s "$extract.part" ]]; then
  mv "$extract.part" "$extract"
else
  rm -f "$extract.part"
  echo "    unchanged since the last download"
fi
echo "    $extract ($(size "$extract"))"

# The trimmed extract, made again when the extract is newer or the filter changed: a stamp beside it records the filter it was made with.
trails="$dir/$region-trails.osm.pbf"
filter=(w/highway w/piste:type w/aerialway r/route)
stamp="$trails.filter"
if [[ ! -f "$trails" || "$extract" -nt "$trails" || "$(cat "$stamp" 2>/dev/null)" != "${filter[*]}" ]]; then
  osmium tags-filter "$extract" "${filter[@]}" -O -o "$trails.part.osm.pbf"
  mv "$trails.part.osm.pbf" "$trails"
  echo "${filter[*]}" >"$stamp"
fi
echo "    $trails ($(size "$trails"))"

# Agency trails: the services give no reliable modified date, so each run fetches them again.
feature_service() { # <service url> <layer>
  echo "$1/FeatureServer/$2/query?where=1%3D1&outFields=*&geometry=$west,$south,$east,$north&geometryType=esriGeometryEnvelope&inSR=4326&outSR=4326&f=json"
}
for kind in $AGENCIES; do
  out="$dir/$kind.geojsonl"
  rm -f "$out.part"
  echo "==> $kind"
  case "$kind" in
    usfs)
      CPL_VSIL_CURL_ALLOWED_EXTENSIONS=.zip,.gdb,.gdbtable,.gdbtablx,.gdbindexes,.spx,.atx,.freelist ogr2ogr -f GeoJSONSeq \
        -t_srs EPSG:4326 -spat "$west" "$south" "$east" "$north" -spat_srs EPSG:4326 "$out.part" \
        /vsizip//vsicurl/https://data.fs.usda.gov/geodata/edw/edw_resources/fc/S_USA.TrailNFS_Publish.gdb.zip TrailNFS_Publish
      ;;
    nps)
      ogr2ogr -f GeoJSONSeq -t_srs EPSG:4326 -spat "$west" "$south" "$east" "$north" -spat_srs EPSG:4326 "$out.part" \
        "$(feature_service https://services2.arcgis.com/FiaPA4ga0iQKduv3/arcgis/rest/services/National_Park_Service_Trails 0)"
      ;;
    blm)
      ogr2ogr -f GeoJSONSeq -t_srs EPSG:4326 -spat "$west" "$south" "$east" "$north" -spat_srs EPSG:4326 "$out.part" \
        "$(feature_service https://services1.arcgis.com/KbxwQRRfWyEYLgp4/arcgis/rest/services/BLM_Natl_GTLF_Public_Managed_Trails 2)"
      ;;
    *)
      echo "$preset: unknown agency $kind (usfs, nps or blm)" >&2
      exit 2
      ;;
  esac
  mv "$out.part" "$out"
  features=$(wc -l <"$out" | tr -d ' ')
  echo "    $out ($(size "$out"), $features features)"
  # A service outage often answers with no features rather than an error.
  [[ $features -gt 0 ]] || echo "    warning: no $kind trails in the box; check the service before importing" >&2
done
