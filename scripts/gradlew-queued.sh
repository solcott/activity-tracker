#!/usr/bin/env bash
# Runs ./gradlew with the given arguments once one of GRADLE_SLOTS (default 2) machine-wide build slots is free, so
# several worktrees' builds queue instead of swapping each other out. The slots live under ~/.gradle, which every
# checkout and session shares (TMPDIR differs per session). A slot whose holder died is reclaimed.
set -euo pipefail
cd "$(dirname "$0")/.."

slots=${GRADLE_SLOTS:-2}
dir="${GRADLE_USER_HOME:-$HOME/.gradle}/activitytracker-build-slots"
mkdir -p "$dir"

held=""
# shellcheck disable=SC2329 # invoked by the traps below
release() {
  [[ -n $held ]] && rm -rf "$held"
  held=""
}

# True when the slot's holder is gone: its process exited, or it never wrote a pid and the slot is a minute old.
stale() {
  local slot=$1 pid
  if [[ -f $slot/pid ]]; then
    pid=$(<"$slot/pid")
    [[ -n $pid ]] && ! kill -0 "$pid" 2>/dev/null
  else
    [[ -n $(find "$slot" -maxdepth 0 -mmin +1 2>/dev/null) ]]
  fi
}

try_acquire() {
  local i slot
  for ((i = 1; i <= slots; i++)); do
    slot="$dir/slot-$i"
    if mkdir "$slot" 2>/dev/null; then
      echo $$ >"$slot/pid"
      held=$slot
      return 0
    fi
    if stale "$slot"; then
      rm -rf "$slot"
      if mkdir "$slot" 2>/dev/null; then
        echo $$ >"$slot/pid"
        held=$slot
        return 0
      fi
    fi
  done
  return 1
}

trap release EXIT
trap 'release; exit 130' INT
trap 'release; exit 143' TERM

waited=0
until try_acquire; do
  if ((waited % 30 == 0)); then
    echo "gradlew-queued: waiting for a Gradle build slot ($slots busy; GRADLE_SLOTS raises the limit)" >&2
  fi
  sleep 2
  waited=$((waited + 2))
done

status=0
./gradlew "$@" || status=$?
exit "$status"
