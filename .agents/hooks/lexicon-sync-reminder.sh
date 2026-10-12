#!/usr/bin/env bash
# PostToolUse (write_to_file|replace_file_content|Edit|Write|MultiEdit):
# After touching one layer of a lexicon, name the layers that must change with it.
set -euo pipefail

input=$(cat)
root="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
file=$(jq -r '.toolCall.args.TargetFile // .tool_input.file_path // empty' <<<"$input")
[[ -z $file ]] && { echo "{}"; exit 0; }
rel="${file#"$root"/}"
L=core/lexicon/src/commonMain/kotlin/io/github/solcott/activitytracker/lexicon
A=server/appview/src/main/kotlin/io/github/solcott/activitytracker/appview

case "$rel" in
  lexicons/*.json)
    msg="You edited lexicon JSON ($rel). Keep the layers in step: Kotlin model ($L/app/Records.kt, Queries.kt or Views.kt), Nsid.kt (+ indexedCollections for indexed records), and the AppView ($A/index/Indexer.kt, db/Tables.kt + a new Flyway migration, api/Views.kt, api/Routes.kt)."
    ;;
  "$L"/app/Records.kt | "$L"/Nsid.kt)
    msg="You edited the record mirror ($rel). Check that lexicons/**.json matches, and that the AppView indexer, tables, migration and delete paths ($A/index/Indexer.kt, $A/db/Tables.kt) handle the change."
    ;;
  "$L"/app/Queries.kt | "$L"/app/Views.kt)
    msg="You edited the query/view mirror ($rel). Check the lexicon JSON, the AppView handler ($A/api/Views.kt, $A/api/Routes.kt), and the client call sites in core/data."
    ;;
  *) echo "{}"; exit 0 ;;
esac
msg="$msg Run the lexicon-auditor agent or skill when the change is complete."
echo "$msg" >&2
jq -n --arg m "$msg" '{
  hookSpecificOutput: {
    hookEventName: "PostToolUse",
    additionalContext: $m
  }
}'
