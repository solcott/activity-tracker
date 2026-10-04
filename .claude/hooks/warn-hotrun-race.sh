#!/usr/bin/env bash
# PreToolUse (Bash): warn when a Gradle build starts while `:desktopApp:hotRun --auto` is running.
# Its continuous build shares the build/ directories and makes concurrent builds fail with bogus
# errors in modules nobody touched. :desktopApp:hotMcpServer compiles nothing and is not matched.
cmd=$(jq -r '.tool_input.command // empty')
grep -q 'gradlew' <<<"$cmd" || exit 0
grep -qE 'hotRun|hotMcpServer|:reload|--stop' <<<"$cmd" && exit 0

# Bracketed first characters keep grep from matching its own command line.
ps -axo command | grep -E '[h]otRun' | grep -qE '[-]-auto|[-]-continuous' || exit 0

msg='A `:desktopApp:hotRun --auto` continuous build is running. It shares build/ with this Gradle run, which can fail with errors in modules you did not touch. Stop it first (pkill -f "desktopApp:hotRun"), or treat such a failure as contention and re-run.'
jq -n --arg m "$msg" '{systemMessage: $m,
  hookSpecificOutput: {hookEventName: "PreToolUse", additionalContext: $m}}'
