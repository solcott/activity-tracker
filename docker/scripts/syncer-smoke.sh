#!/usr/bin/env bash
# Spike for add-activity-visibility: checks that a service with its own did:web can sync an atproto Spaces space on the local
# PDS. Starts a stub (syncer-smoke.py) on a second quick tunnel, so the PDS can resolve its DID document and reach its
# notifyWrite endpoint, then runs the check as <name>.test. --blob also round-trips a blob through a space record.
# Usage: docker/scripts/syncer-smoke.sh alice [--blob] [pds-url]
set -euo pipefail

cd "$(dirname "$0")/.."
[[ -f .env ]] || { echo "docker/.env missing: cp docker/.env.example docker/.env" >&2; exit 1; }
# shellcheck disable=SC1091
source .env

name="${1:?usage: syncer-smoke.sh <name> [--blob] [pds-url]}"
shift
blob=""
if [[ ${1:-} == --blob ]]; then
  blob="--blob"
  shift
fi
pds="${1:-http://localhost:2583}"
stub_port=8099
metrics_port=20299

command -v cloudflared >/dev/null || { echo "cloudflared not installed: brew install cloudflared" >&2; exit 1; }
log=$(mktemp -t syncer-smoke-tunnel)
cloudflared tunnel --url "http://localhost:$stub_port" --metrics "127.0.0.1:$metrics_port" >"$log" 2>&1 &
tunnel_pid=$!
trap 'kill $tunnel_pid 2>/dev/null || true' EXIT

host=""
for _ in $(seq 30); do
  host=$(curl -s -m 2 "http://127.0.0.1:$metrics_port/quicktunnel" 2>/dev/null | sed -nE 's/.*"hostname":"([^"]+)".*/\1/p' || true)
  [[ -n $host ]] && break
  sleep 1
done
[[ -n $host ]] || { echo "FAIL cloudflared didn't report a hostname; see $log" >&2; exit 1; }
for _ in $(seq 30); do
  curl -s -m 2 "http://127.0.0.1:$metrics_port/metrics" 2>/dev/null | grep -qE '^cloudflared_tunnel_ha_connections [1-9]' && break
  sleep 1
done
# Wait for the hostname to resolve publicly before anything (the PDS included) looks it up, so no resolver caches an NXDOMAIN.
for _ in $(seq 30); do
  [[ -n $(dig +short +time=2 +tries=1 A "$host" @1.1.1.1 2>/dev/null | grep -E '^[0-9.]+$' || true) ]] && break
  sleep 2
done
echo "ok   stub tunnel https://$host"

python3 -I "$(pwd)/scripts/syncer-smoke.py" "$name" "$pds" "$host" "$stub_port" "$TEST_ACCOUNT_PASSWORD" $blob
