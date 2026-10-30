#!/usr/bin/env bash
# Prints "true" when the Gradle build must run for the change from <base> to <head>, else "false". CI skips the build when
# every changed file is documentation or agent configuration, which nothing in the build reads:
#   *.md anywhere, openspec/, .agents/, .claude/
# Anything else (lexicons, scripts, gradle/, config/, workflows) counts as code. When <base> is missing, all zeros (a new
# branch) or can't be fetched (a force push), it prints "true": building when unsure is always safe.
# Usage: scripts/ci-code-changed.sh <base> <head>
set -euo pipefail

base="${1:-}"
head="${2:?usage: ci-code-changed.sh <base> <head>}"

if [[ -z "$base" || "$base" =~ ^0+$ ]]; then
  echo true
  exit 0
fi

# A shallow checkout has only <head>. The diff needs just the two trees, so one commit of <base> is enough.
if ! git cat-file -e "$base^{commit}" 2>/dev/null && ! git fetch --quiet --no-tags --depth=1 origin "$base" 2>/dev/null; then
  echo true
  exit 0
fi

if git diff --name-only "$base" "$head" | grep -qvE '(\.md$|^openspec/|^\.agents/|^\.claude/)'; then
  echo true
else
  echo false
fi
