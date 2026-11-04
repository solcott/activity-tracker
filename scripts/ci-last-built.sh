#!/usr/bin/env bash
# Prints the commit of the latest push to <branch> whose CI `check` job ran and passed, or nothing when none of the recent
# runs did. CI diffs a push against this commit, not the previous push, so a docs-only push skips the build only when the
# code it carries has already been built and tested: a push whose build failed, was cancelled or was itself skipped never
# counts. Needs `gh` with GH_TOKEN and `actions: read`.
# Usage: scripts/ci-last-built.sh <branch>
set -euo pipefail

branch="${1:?usage: ci-last-built.sh <branch>}"
repo="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is not set}"

# Runs whose workflow succeeded, newest first. A skipped `check` still counts as success, so look at the job itself.
runs=$(gh api "repos/$repo/actions/workflows/ci.yml/runs?branch=$branch&event=push&status=success&per_page=20" \
  --jq '.workflow_runs[] | "\(.id) \(.head_sha)"') || exit 0

while read -r id sha; do
  [[ -n "$id" ]] || continue
  conclusion=$(gh api "repos/$repo/actions/runs/$id/jobs" --jq '.jobs[] | select(.name == "check") | .conclusion') || continue
  if [[ "$conclusion" == success ]]; then
    echo "$sha"
    exit 0
  fi
done <<< "$runs"
