#!/usr/bin/env bash
# Fails when a tracked file could reveal personal data in the public repository (dev/public-repository):
#   1. an activity file (FIT, GPX, TCX, KML, KMZ, UDDF, Subsurface, or their .gz) with no LICENSE* or README* beside it
#   2. an image or video under openspec/ (verification screenshots stay local)
#   3. an absolute path into a home folder (/Users/<name>/ or /home/<name>/)
# Prints one "path[:line]: reason" per finding and exits 1, or exits 0 silently. Untracked files are ignored.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

FAILED=0
SELF="scripts/check-public-safety.sh"

# 1. Activity files need a license or source note in the same directory.
while IFS= read -r -d '' file; do
  dir="$(dirname "$file")"
  if ! git ls-files -- "$dir" | grep -qiE "^$(printf '%s' "$dir" | sed 's/[][\.*^$]/\\&/g')/(LICENSE|README)[^/]*$"; then
    echo "$file: activity file with no LICENSE or README beside it"
    FAILED=1
  fi
done < <(git ls-files -z | grep -ziE '\.(fit|gpx|tcx|kml|kmz|uddf|ssrf)(\.gz)?$' || true)

# 2. No screenshots or recordings under openspec/.
while IFS= read -r -d '' file; do
  echo "$file: image or video under openspec/"
  FAILED=1
done < <(git ls-files -z -- openspec | grep -ziE '\.(png|jpe?g|gif|webp|heic|mp4|mov)$' || true)

# 3. No absolute home-folder paths in tracked text files.
paths="$(git grep -nIE '/(Users|home)/[A-Za-z][A-Za-z0-9._-]*/' -- . ":(exclude)$SELF" || true)"
if [[ -n "$paths" ]]; then
  printf '%s\n' "$paths" | sed -E 's/^([^:]+:[0-9]+):.*/\1: absolute home-folder path/'
  FAILED=1
fi

exit "$FAILED"
