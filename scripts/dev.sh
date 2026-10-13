#!/usr/bin/env bash
# One command for the local dev environment: Docker stack, cloudflared tunnel, local.properties,
# AppView, adb reverse and (when the tunnel URL changed) the Android debug app. Every step checks
# the current state first, so re-running only fixes what is missing or stale.
#
#   scripts/dev.sh [up] [options]   bring everything up (default)
#   scripts/dev.sh status           show what is running
#   scripts/dev.sh down             stop the AppView and tunnel, stop (not delete) the containers
#
# up options:
#   --seed NAME        create NAME.test if it doesn't exist (repeatable); runs after the AppView is up
#   --android          always (re)install the Android debug app on connected devices
#   --no-android       skip adb reverse and the Android install
#   --no-tunnel        skip cloudflared and local.properties (web-only work; atproto Spaces need the tunnel)
#   --restart-appview  restart the AppView even if it is healthy and current
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

STATE_DIR="$ROOT/.dev"
COMPOSE=(docker compose -f docker/compose.yaml)
PROPS="$ROOT/local.properties"
PROP_KEY="activitytracker.appviewPublicUrl"
APPVIEW_PORT=8081
ADB_PORTS=(2582 2583 "$APPVIEW_PORT")
METRICS_PORTS=(20241 20242 20243 20244 20245)

if [[ -t 1 ]]; then
  BOLD=$'\e[1m' GREEN=$'\e[32m' YELLOW=$'\e[33m' RED=$'\e[31m' RESET=$'\e[0m'
else
  BOLD="" GREEN="" YELLOW="" RED="" RESET=""
fi
step() { echo "${BOLD}==> $*${RESET}"; }
ok() { echo "    ${GREEN}✓${RESET} $*"; }
warn() { echo "    ${YELLOW}!${RESET} $*"; }
die() {
  echo "    ${RED}✗${RESET} $*" >&2
  exit 1
}

http_code() { curl -s -o /dev/null -m "${2:-5}" -w '%{http_code}' "$1" 2>/dev/null || true; }

# Polls until `http_code URL` is 2xx. Args: url, timeout seconds, label.
wait_http() {
  local url=$1 timeout=$2 label=$3 waited=0
  until [[ $(http_code "$url") == 2* ]]; do
    ((waited >= timeout)) && return 1
    sleep 2
    waited=$((waited + 2))
  done
  ok "$label"
}

# ---------------------------------------------------------------- docker

ensure_docker() {
  step "Docker stack"
  if ! docker info >/dev/null 2>&1; then
    [[ $(uname) == Darwin ]] || die "Docker isn't running"
    warn "Docker isn't running; starting Docker Desktop"
    open -a Docker
    local waited=0
    until docker info >/dev/null 2>&1; do
      ((waited >= 120)) && die "Docker didn't start within 120s"
      sleep 3
      waited=$((waited + 3))
    done
  fi
  if [[ ! -f docker/.env ]]; then
    cp docker/.env.example docker/.env
    ok "created docker/.env from .env.example"
  fi
  # Idempotent: only (re)creates containers whose config changed. The first run builds PLC, the
  # relay and Jetstream from source and takes a few minutes.
  "${COMPOSE[@]}" up -d --remove-orphans
  wait_http "http://localhost:2583/xrpc/_health" 180 "PDS :2583" || die "PDS not healthy; see: ${COMPOSE[*]} logs pds"
  wait_http "http://localhost:2470/xrpc/_health" 180 "relay :2470" || die "relay not healthy; see: ${COMPOSE[*]} logs relay"
  local waited=0
  until "${COMPOSE[@]}" exec -T appview-db pg_isready -U appview >/dev/null 2>&1; do
    ((waited >= 60)) && die "AppView Postgres not ready; see: ${COMPOSE[*]} logs appview-db"
    sleep 2
    waited=$((waited + 2))
  done
  ok "AppView Postgres :5433"
}

# ---------------------------------------------------------------- lexicons

# The PDS resolves our space-type lexicons from lexicons.test (PDS_LEXICON_AUTHORITY_DID); without them,
# OAuth refuses the space: scope. The account's DID only exists once the PDS runs, so the first run on a
# fresh volume writes it to docker/.env and recreates the PDS; later runs find it unchanged.
ensure_lexicons() {
  step "Lexicon authority"
  local did current
  did=$(docker/scripts/publish-lexicons.sh 2> >(sed 's/^/    /' >&2)) || die "publishing lexicons failed"
  current=$(sed -nE 's/^PDS_LEXICON_AUTHORITY_DID=(.*)$/\1/p' docker/.env)
  if [[ $current != "$did" ]]; then
    if grep -q '^PDS_LEXICON_AUTHORITY_DID=' docker/.env; then
      sed -i.bak "s|^PDS_LEXICON_AUTHORITY_DID=.*|PDS_LEXICON_AUTHORITY_DID=$did|" docker/.env && rm docker/.env.bak
    else
      printf '\nPDS_LEXICON_AUTHORITY_DID=%s\n' "$did" >>docker/.env
    fi
    # Not `up -d pds`: the relay and Jetstream share the PDS's network namespace, and only a full `up`
    # recreates them along with it.
    "${COMPOSE[@]}" up -d --remove-orphans
    wait_http "http://localhost:2583/xrpc/_health" 180 "PDS recreated with lexicon authority $did" \
      || die "PDS not healthy; see: ${COMPOSE[*]} logs pds"
    wait_http "http://localhost:2470/xrpc/_health" 180 "relay :2470" || die "relay not healthy; see: ${COMPOSE[*]} logs relay"
  else
    ok "lexicons.test ($did)"
  fi
}

# ---------------------------------------------------------------- tunnel

# Prints the hostname of a running quick tunnel to the AppView, if any (from cloudflared's metrics).
running_tunnel_host() {
  local port host
  for port in "${METRICS_PORTS[@]}"; do
    host=$(curl -s -m 2 "http://127.0.0.1:$port/quicktunnel" 2>/dev/null | sed -nE 's/.*"hostname":"([^"]+)".*/\1/p')
    if [[ -n $host ]]; then
      echo "$host"
      return
    fi
  done
}

tunnel_pids() { pgrep -f "cloudflared tunnel --url http://localhost:$APPVIEW_PORT" || true; }

# Tunnel hostnames are resolved with a public resolver, never the system one: a fresh quick
# tunnel's record doesn't exist for its first few seconds, and macOS's mDNSResponder caches that
# NXDOMAIN for trycloudflare.com's 60 s negative TTL, re-caching it on every poll. Prints the IP,
# nothing if the record doesn't exist yet; fails only if 1.1.1.1 can't be reached at all.
tunnel_ip() {
  local out
  out=$(dig +short +time=2 +tries=1 A "$1" @1.1.1.1 2>/dev/null) || return 1
  grep -E '^[0-9.]+$' <<<"$out" | head -n 1 || true
}

# http_code for https://HOST/PATH, pinned to tunnel_ip's answer. Args: host, path, timeout seconds.
# Falls back to the system resolver only when the public one is unreachable (a filtered network).
tunnel_http_code() {
  local ip
  if ! ip=$(tunnel_ip "$1"); then
    http_code "https://$1$2" "${3:-5}"
    return
  fi
  if [[ -z $ip ]]; then
    echo 000
    return
  fi
  curl -s -o /dev/null -m "${3:-5}" -w '%{http_code}' --resolve "$1:443:$ip" "https://$1$2" 2>/dev/null || true
}

# A quick tunnel answers through Cloudflare's edge even when the AppView behind it is down (502),
# so any HTTP response means the hostname is live. 000 means DNS or the edge doesn't know it.
tunnel_alive() { [[ $(tunnel_http_code "$1" / 10) != 000 ]]; }

# Whether the cloudflared on this metrics port holds at least one edge connection (it logs
# "Registered tunnel connection" at the same time). Before that, the hostname can't answer.
tunnel_registered() {
  curl -s -m 2 "http://127.0.0.1:$1/metrics" 2>/dev/null | grep -qE '^cloudflared_tunnel_ha_connections [1-9]'
}

ensure_tunnel() {
  step "cloudflared tunnel"
  command -v cloudflared >/dev/null || die "cloudflared not installed: brew install cloudflared"
  local host
  host=$(running_tunnel_host)
  if [[ -n $host ]] && tunnel_alive "$host"; then
    ok "already running: https://$host"
  else
    if [[ -n $(tunnel_pids) ]]; then
      warn "tunnel ${host:-<unknown>} isn't reachable; restarting it"
      pkill -f "cloudflared tunnel --url http://localhost:$APPVIEW_PORT" || true
      sleep 1
    fi
    mkdir -p "$STATE_DIR"
    nohup cloudflared tunnel --url "http://localhost:$APPVIEW_PORT" --metrics "127.0.0.1:${METRICS_PORTS[0]}" \
      >"$STATE_DIR/cloudflared.log" 2>&1 &
    local waited=0
    until host=$(running_tunnel_host) && [[ -n $host ]]; do
      ((waited >= 30)) && die "cloudflared didn't report a hostname; see .dev/cloudflared.log"
      sleep 1
      waited=$((waited + 1))
    done
    waited=0
    until tunnel_registered "${METRICS_PORTS[0]}"; do
      ((waited >= 30)) && die "cloudflared didn't register a connection; see .dev/cloudflared.log"
      sleep 1
      waited=$((waited + 1))
    done
    waited=0
    until tunnel_alive "$host"; do
      ((waited >= 60)) && die "https://$host isn't reachable yet; see .dev/cloudflared.log"
      sleep 2
      waited=$((waited + 2))
    done
    ok "started: https://$host (log: .dev/cloudflared.log)"
  fi
  TUNNEL_URL="https://$host"
}

# ---------------------------------------------------------------- local.properties

configured_url() { [[ -f $PROPS ]] && grep "^$PROP_KEY=" "$PROPS" | tail -1 | cut -d= -f2- || true; }

# Sets the property to $TUNNEL_URL, dropping duplicates (the last line wins, which hid a stale
# placeholder once). Sets URL_CHANGED when the effective value changes.
ensure_local_properties() {
  step "local.properties"
  local current count
  current=$(configured_url)
  count=$( (grep -c "^$PROP_KEY=" "$PROPS" 2>/dev/null) || true)
  if [[ $current == "$TUNNEL_URL" && ${count:-0} -eq 1 ]]; then
    ok "$PROP_KEY is current"
    return
  fi
  local tmp
  tmp=$(mktemp)
  [[ -f $PROPS ]] && { grep -v "^$PROP_KEY=" "$PROPS" >"$tmp" || true; }
  echo "$PROP_KEY=$TUNNEL_URL" >>"$tmp"
  mv "$tmp" "$PROPS"
  [[ $current != "$TUNNEL_URL" ]] && URL_CHANGED=1
  ok "$PROP_KEY=$TUNNEL_URL${current:+ (was $current)}"
}

# ---------------------------------------------------------------- AppView

appview_pid() { lsof -nP -tiTCP:"$APPVIEW_PORT" -sTCP:LISTEN 2>/dev/null | head -1 || true; }

# The `./gradlew :server-appview:run` client of this checkout. It outlives the AppView JVM by a few
# seconds and then prints "BUILD FAILED" etc. into the log it was started with.
gradle_client_pids() { pgrep -f "$ROOT/gradle/wrapper/gradle-wrapper.jar :server-appview:run" || true; }

# The client_id the running AppView advertises, which is derived from the URL it started with.
served_client_id() {
  curl -s -m 3 "http://localhost:$APPVIEW_PORT/oauth/client-metadata.json" 2>/dev/null |
    sed -nE 's/.*"client_id":"([^"]+)".*/\1/p'
}

stop_appview() {
  local pid cmd
  pid=$(appview_pid)
  [[ -z $pid ]] && return 0
  cmd=$(ps -o command= -p "$pid" || true)
  if [[ $cmd != *appview* && $cmd != *ApplicationKt* ]]; then
    die "port $APPVIEW_PORT is held by something else (pid $pid: ${cmd:0:80}); stop it first"
  fi
  kill "$pid"
  local waited=0
  while [[ -n $(appview_pid) ]]; do
    ((waited >= 20)) && kill -9 "$pid" 2>/dev/null || true
    ((waited >= 25)) && die "AppView (pid $pid) didn't stop"
    sleep 1
    waited=$((waited + 1))
  done
  ok "stopped AppView (pid $pid)"
  waited=0
  while [[ -n $(gradle_client_pids) ]]; do
    ((waited == 10)) && { gradle_client_pids | xargs kill 2>/dev/null || true; }
    ((waited >= 15)) && die "gradle client ($(gradle_client_pids | xargs)) didn't exit"
    sleep 1
    waited=$((waited + 1))
  done
}

ensure_appview() {
  step "AppView :$APPVIEW_PORT"
  local expected="" reason=""
  [[ -n ${TUNNEL_URL:-} ]] && expected="$TUNNEL_URL/oauth/client-metadata.json"
  if [[ -n $(appview_pid) ]]; then
    if [[ $(http_code "http://localhost:$APPVIEW_PORT/xrpc/_health") != 2* ]]; then
      reason="running but unhealthy"
    elif [[ -n $expected && $(served_client_id) != "$expected" ]]; then
      reason="serving $(served_client_id), not the current tunnel"
    elif [[ ${RESTART_APPVIEW:-0} == 1 ]]; then
      reason="--restart-appview"
    else
      ok "running and current"
      return
    fi
    warn "restarting: $reason"
    stop_appview
  fi
  mkdir -p "$STATE_DIR"
  # A fresh file: anything still holding the old one (a lingering gradle client) writes to the .prev.
  [[ -f $STATE_DIR/appview.log ]] && mv "$STATE_DIR/appview.log" "$STATE_DIR/appview.prev.log"
  nohup ./gradlew :server-appview:run >"$STATE_DIR/appview.log" 2>&1 &
  echo "    starting (log: .dev/appview.log); the first build can take a minute"
  if ! wait_http "http://localhost:$APPVIEW_PORT/xrpc/_health" 300 "AppView healthy"; then
    tail -n 30 "$STATE_DIR/appview.log" >&2 || true
    die "AppView didn't become healthy"
  fi
  if [[ -n $expected ]]; then
    [[ $(served_client_id) == "$expected" ]] || die "AppView serves $(served_client_id), expected $expected"
    local host=${TUNNEL_URL#https://} waited=0
    until [[ $(tunnel_http_code "$host" /oauth/client-metadata.json) == 2* ]]; do
      ((waited >= 60)) && die "$expected isn't reachable; check .dev/cloudflared.log"
      sleep 2
      waited=$((waited + 2))
    done
    ok "client metadata reachable through the tunnel"
  fi
}

# ---------------------------------------------------------------- Android

adb_devices() { adb devices 2>/dev/null | awk 'NR > 1 && $2 == "device" { print $1 }'; }

ensure_android() {
  step "Android"
  if ! command -v adb >/dev/null; then
    warn "adb not found; skipping"
    return
  fi
  local devices serial port
  devices=$(adb_devices)
  if [[ -z $devices ]]; then
    warn "no device or emulator connected; re-run once one is up"
    return
  fi
  for serial in $devices; do
    local added=()
    for port in "${ADB_PORTS[@]}"; do
      if ! adb -s "$serial" reverse --list | grep -q "tcp:$port tcp:$port"; then
        adb -s "$serial" reverse "tcp:$port" "tcp:$port" >/dev/null
        added+=("$port")
      fi
    done
    if ((${#added[@]})); then
      ok "$serial: adb reverse ${added[*]}"
    else
      ok "$serial: adb reverse already set"
    fi
  done
  # The redirect scheme is compiled into the app, so a new tunnel URL needs a reinstall.
  if [[ ${URL_CHANGED:-0} == 1 || ${FORCE_ANDROID:-0} == 1 ]]; then
    echo "    installing the debug app (${URL_CHANGED:+tunnel URL changed}${FORCE_ANDROID:+--android})"
    ./gradlew -q :androidApp:installDebug || die "installDebug failed"
    ok "installed on: $(echo $devices)"
  fi
}

# ---------------------------------------------------------------- seed

ensure_accounts() {
  [[ ${#SEEDS[@]} -eq 0 ]] && return
  step "Accounts"
  local name
  for name in "${SEEDS[@]}"; do
    if [[ $(http_code "http://localhost:2583/xrpc/com.atproto.identity.resolveHandle?handle=$name.test") == 2* ]]; then
      ok "$name.test exists"
    else
      docker/scripts/create-account.sh "$name" | sed 's/^/    /'
    fi
  done
}

# ---------------------------------------------------------------- commands

cmd_up() {
  SEEDS=()
  while (($#)); do
    case $1 in
      --seed) SEEDS+=("${2:?--seed needs a name}"); shift ;;
      --android) FORCE_ANDROID=1 ;;
      --no-android) NO_ANDROID=1 ;;
      --no-tunnel) NO_TUNNEL=1 ;;
      --restart-appview) RESTART_APPVIEW=1 ;;
      *) die "unknown option: $1 (see the header of scripts/dev.sh)" ;;
    esac
    shift
  done
  ensure_docker
  ensure_lexicons
  if [[ ${NO_TUNNEL:-0} != 1 ]]; then
    ensure_tunnel
    ensure_local_properties
  else
    step "cloudflared tunnel"
    warn "skipped: the PDS can't reach the AppView's Spaces checkUserAccess without it"
  fi
  ensure_appview
  [[ ${NO_ANDROID:-0} != 1 ]] && ensure_android
  ensure_accounts
  echo
  echo "${BOLD}Ready.${RESET} Web: ./gradlew :webApp:wasmJsBrowserDevelopmentRun → http://127.0.0.1:8080"
  if [[ ${URL_CHANGED:-0} == 1 ]]; then
    echo "${YELLOW}The tunnel URL changed: rebuild the iOS app too (its redirect scheme is compiled in).${RESET}"
  fi
}

cmd_status() {
  step "Docker"
  if docker info >/dev/null 2>&1; then
    "${COMPOSE[@]}" ps --format 'table {{.Service}}\t{{.Status}}' | sed 's/^/    /'
  else
    warn "Docker isn't running"
  fi
  step "Tunnel"
  local host
  host=$(running_tunnel_host)
  if [[ -z $host ]]; then
    warn "not running (the PDS can't reach the AppView's Spaces checkUserAccess without it)"
  elif tunnel_alive "$host"; then
    ok "https://$host"
  else
    warn "https://$host (not reachable)"
  fi
  step "local.properties"
  local url
  url=$(configured_url)
  if [[ -z $url ]]; then
    warn "$PROP_KEY not set"
  elif [[ -n $host && $url != "https://$host" ]]; then
    warn "$url (doesn't match the running tunnel)"
  else
    ok "$url"
  fi
  step "AppView"
  if [[ $(http_code "http://localhost:$APPVIEW_PORT/xrpc/_health") == 2* ]]; then
    ok "healthy; client_id $(served_client_id)"
  else
    warn "not running"
  fi
  step "Android"
  if command -v adb >/dev/null && [[ -n $(adb_devices) ]]; then
    local serial
    for serial in $(adb_devices); do
      ok "$serial: reverse $(adb -s "$serial" reverse --list | awk '{ print $2 }' | sed 's/tcp://' | tr '\n' ' ')"
    done
  else
    warn "no device connected"
  fi
}

cmd_down() {
  step "AppView"
  if [[ -n $(appview_pid) ]]; then stop_appview; else ok "not running"; fi
  step "Tunnel"
  if [[ -n $(tunnel_pids) ]]; then
    pkill -f "cloudflared tunnel --url http://localhost:$APPVIEW_PORT"
    ok "stopped"
  else
    ok "not running"
  fi
  step "Docker stack"
  if docker info >/dev/null 2>&1; then
    # stop, not down: keeps containers and volumes (accounts, records, the AppView DB).
    "${COMPOSE[@]}" stop
    ok "stopped (data kept)"
  else
    ok "Docker isn't running"
  fi
}

case ${1:-up} in
  up) shift || true; cmd_up "$@" ;;
  status) cmd_status ;;
  down) cmd_down ;;
  -h | --help) sed -n '2,17p' "$0" | sed 's/^# \{0,1\}//' ;;
  -*) cmd_up "$@" ;;
  *) die "unknown command: $1 (up, status, down)" ;;
esac
