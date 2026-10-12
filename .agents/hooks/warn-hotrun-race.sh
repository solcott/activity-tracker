#!/usr/bin/env bash
# PreToolUse (run_command|Bash): warn when a Gradle build starts while `:desktopApp:hotRun --auto` is running.
# No pipefail: grep -q exiting early would SIGPIPE the ps pipeline and hide a running hotRun.
set -eu

input=$(cat)
claude=$(jq 'has("tool_input")' <<<"$input")
allow() { [[ $claude == true ]] || echo '{"decision": "allow"}'; exit 0; }
cmd=$(jq -r '.toolCall.args.CommandLine // .tool_input.command // empty' <<<"$input")

if ! grep -q 'gradlew' <<<"$cmd"; then
  allow
fi

if grep -qE 'hotRun|hotMcpServer|:reload|--stop' <<<"$cmd"; then
  allow
fi

# Bracketed first characters keep grep from matching its own command line.
if ! ps -axo command 2>/dev/null | grep -E '[h]otRun' | grep -qE '[-]-auto|[-]-continuous'; then
  allow
fi

msg='A `:desktopApp:hotRun --auto` continuous build is running. It shares build/ with this Gradle run, which can fail with errors in modules you did not touch. Stop it first (pkill -f "desktopApp:hotRun"), or treat such a failure as contention and re-run.'

# Claude: a non-blocking warning. Antigravity: ask before running.
if [[ $claude == true ]]; then
  jq -n --arg m "$msg" '{systemMessage: $m, hookSpecificOutput: {hookEventName: "PreToolUse", additionalContext: $m}}'
else
  jq -n --arg m "$msg" '{decision: "ask", reason: $m}'
fi
exit 0
