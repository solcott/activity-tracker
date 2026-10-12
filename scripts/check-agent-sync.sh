#!/usr/bin/env bash
# Verifies that Claude Code and Antigravity configurations are properly synchronized.
set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$ROOT_DIR"

FAILED=0

if ! command -v jq >/dev/null 2>&1; then
  echo "❌ Error: jq is not installed; it is needed to check the config files."
  exit 1
fi

check_symlink() {
  local link="$1"
  local expected_target="$2"

  if [[ ! -L "$link" ]]; then
    echo "❌ Error: $link is not a symbolic link."
    FAILED=1
    return
  fi

  local target
  target="$(readlink "$link")"
  if [[ "$target" != "$expected_target" ]]; then
    echo "❌ Error: $link points to '$target', expected '$expected_target'."
    FAILED=1
    return
  fi

  if [[ ! -e "$link" ]]; then
    echo "❌ Error: $link is a broken symbolic link (target does not exist)."
    FAILED=1
    return
  fi

  echo "✅ $link -> $target"
}

check_json() {
  local file="$1"
  if [[ ! -f "$file" ]]; then
    echo "❌ Error: $file does not exist."
    FAILED=1
    return
  fi

  if ! jq . "$file" >/dev/null 2>&1; then
    echo "❌ Error: $file contains invalid JSON."
    FAILED=1
    return
  fi

  echo "✅ $file is valid JSON."
}

check_executable() {
  local file="$1"
  if [[ ! -x "$file" ]]; then
    echo "❌ Error: $file is not executable."
    FAILED=1
    return
  fi

  echo "✅ $file is executable."
}

echo "=== Checking Root Guidelines Symlinks ==="
check_symlink "CLAUDE.md" "AGENTS.md"
check_symlink "GEMINI.md" "AGENTS.md"

echo ""
echo "=== Checking Configuration Directory Symlinks ==="
check_symlink ".claude/rules" "../.agents/rules"
check_symlink ".claude/agents" "../.agents/agents"
check_symlink ".claude/hooks" "../.agents/hooks"
check_symlink ".gemini/skills" "../.agents/skills"
check_symlink ".agents/mcp_config.json" "../.mcp.json"

for skill in lexicon-change local-stack new-screen; do
  check_symlink ".claude/skills/$skill" "../../.agents/skills/$skill"
done

echo ""
echo "=== Checking Config File Validity ==="
check_json ".mcp.json"
check_json ".claude/settings.json"
check_json ".agents/hooks.json"

echo ""
echo "=== Checking Hook Parity ==="
claude_hooks="$(jq -r '.. | .command? // empty' .claude/settings.json | grep -oE '[a-z-]+\.sh' | sort -u)"
agents_hooks="$(jq -r '.. | .command? // empty' .agents/hooks.json | grep -oE '[a-z-]+\.sh' | sort -u)"
if [[ "$claude_hooks" != "$agents_hooks" ]]; then
  echo "❌ Error: .claude/settings.json and .agents/hooks.json wire different hooks:"
  diff <(echo "$claude_hooks") <(echo "$agents_hooks") || true
  FAILED=1
else
  echo "✅ Both configs wire: $(echo $claude_hooks)"
fi

echo ""
echo "=== Checking OpenSpec Command Parity ==="
claude_cmds="$(cd .claude/commands/opsx && ls *.md | sed 's/\.md$//')"
gemini_cmds="$(cd .gemini/commands/opsx && ls *.toml | sed 's/\.toml$//')"
if [[ "$claude_cmds" != "$gemini_cmds" ]]; then
  echo "❌ Error: .claude/commands/opsx and .gemini/commands/opsx differ:"
  diff <(echo "$claude_cmds") <(echo "$gemini_cmds") || true
  FAILED=1
else
  echo "✅ opsx commands match: $(echo $claude_cmds)"
fi

echo ""
echo "=== Checking For Machine-Specific Links ==="
# Antigravity rewrites paths into absolute file:/// links, which break in worktrees and on other machines.
if abs_links="$(grep -rnI 'file:///' AGENTS.md .agents .claude/skills .claude/commands .gemini/commands 2>/dev/null)"; then
  echo "❌ Error: absolute file:/// links found; use paths relative to the file:"
  echo "$abs_links"
  FAILED=1
else
  echo "✅ No absolute file:/// links."
fi

echo ""
echo "=== Checking Hook Scripts Executability ==="
for hook in .agents/hooks/*.sh; do
  check_executable "$hook"
done

echo ""
if [[ "$FAILED" -eq 0 ]]; then
  echo "🎉 All Claude Code <-> Antigravity synchronization checks passed!"
  exit 0
else
  echo "💥 One or more synchronization checks failed."
  exit 1
fi
