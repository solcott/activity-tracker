#!/usr/bin/env bash
# Records a Perfetto system trace of the Android debug app with Compose composition and recomposition tracing: every composable as a
# slice, and each state write linked by a flow arrow to the recomposition it caused (androidx.compose.runtime:runtime-tracing, debug
# builds only). Runs Google's record_recomposition_trace.py (android/performance-samples, Apache-2.0), fetched once at a pinned commit and
# checked against its SHA-256, and writes a zip to open in https://ui.perfetto.dev.
#
#   scripts/compose-trace.sh [-t SECONDS] [any other record_recomposition_trace.py option, e.g. --no-recompose, -b MB, -s SERIAL]
#
# Install and open the debug app first (./gradlew :androidApp:installDebug), get to the screen to trace, then run this and use the app;
# Ctrl+C stops (or -t). Needs a device or emulator on API 29+ over adb, and Python 3.10+. Recomposition tracing slows composition, so
# read the trace for causes (which write recomposed what), not for timings.
set -euo pipefail

cd "$(dirname "$0")/.."
commit=e16ae334700295a43cf97b5ed20a0afb4eb880c6
sha256=6eeecb87f5205d6beb76dbf2c512d6b60168a770b5bd94e5cec9f1376d4c4799
package=io.github.solcott.activitytracker
dir=build/compose-trace
script="$dir/record_recomposition_trace-${commit:0:12}.py"

command -v adb >/dev/null || { echo "Put adb (Android SDK platform-tools) on the PATH first" >&2; exit 1; }
command -v python3 >/dev/null || { echo "Install Python 3.10+ first" >&2; exit 1; }
adb get-state >/dev/null 2>&1 || { echo "No device or emulator attached (adb devices)" >&2; exit 1; }
adb shell pm list packages "$package" | grep -qx "package:$package" ||
  { echo "Install the debug app first: ./gradlew :androidApp:installDebug" >&2; exit 1; }

mkdir -p "$dir"
if [[ ! -f "$script" ]]; then
  curl -sfL "https://raw.githubusercontent.com/android/performance-samples/$commit/RecompositionTracing/record_recomposition_trace.py" \
    -o "$script.part"
  echo "$sha256  $script.part" | shasum -a 256 -c --status || { rm -f "$script.part"; echo "Checksum mismatch" >&2; exit 1; }
  mv "$script.part" "$script"
fi

exec python3 -I "$script" -p "$package" -o "$dir" "$@"
