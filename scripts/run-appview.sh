#!/usr/bin/env bash
# Builds the AppView and runs it in the foreground as a plain JVM, with the local-stack environment
# `./gradlew :server-appview:run` gives it (real env vars win). Unlike that task, no Gradle daemon stays busy for as
# long as the AppView runs, so other builds keep the warm one. Arguments go to the AppView.
set -euo pipefail
cd "$(dirname "$0")/.."

./gradlew -q :server-appview:installDist :server-appview:localStackEnv

# Exported line by line rather than sourced: the values (a signing key, a password) are data, not shell.
while IFS= read -r line; do
  [[ $line == *=* ]] && export "${line%%=*}=${line#*=}"
done <server/appview/build/local-stack/appview.env

exec server/appview/build/install/server-appview/bin/server-appview "$@"
