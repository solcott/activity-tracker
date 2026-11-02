#!/usr/bin/env bash
# One command for the local dev environment: Docker stack, labeler (Ozone), cloudflared tunnel,
# local.properties, AppView, adb reverse and (when the tunnel URL changed) the Android debug app. Every step checks
# the current state first, so re-running only fixes what is missing or stale.
#
#   scripts/dev.sh [up] [options]   bring everything up (default)
#   scripts/dev.sh status           show what is running
#   scripts/dev.sh down             stop the AppView and tunnel, stop (not delete) the containers
#   scripts/dev.sh device-setup     one-time named-tunnel setup for device mode (below)
#
# up options:
#   --seed NAME        create NAME.test if it doesn't exist (repeatable); runs after the AppView is up
#   --android          always (re)install the Android debug app on connected devices
#   --no-android       skip adb reverse and the Android install
#   --no-tunnel        skip cloudflared and local.properties (web-only work; atproto Spaces need the tunnel)
#   --restart-appview  restart the AppView even if it is healthy and current
#   --reset [--yes]    delete the stack's volumes (every account and record) first; needed to switch modes
#
# Device mode, the normal setup: set activitytracker.deviceHost in ~/.gradle/gradle.properties (every checkout and worktree)
# or in local.properties (this one) to a subdomain of a zone you have on Cloudflare (e.g. dev.example.com), and run
# device-setup once. The PDS then runs as https://dev.example.com, with PLC on plc-dev.example.com and the AppView on
# appview-dev.example.com, through a named tunnel instead of a quick one, and every client (web and desktop too) uses them.
# Without a host anywhere the stack runs in localhost mode.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

STATE_DIR="$ROOT/.dev"
COMPOSE=(docker compose -f docker/compose.yaml)
PROPS="$ROOT/local.properties"
PROP_KEY="activitytracker.appviewPublicUrl"
LABELER_PROP_KEY="activitytracker.labelerDid"
APPVIEW_PORT=8081
DEVICE_HOST_KEY="activitytracker.deviceHost"
DEVICE_TUNNEL="activitytracker-dev"
# Next to the tunnel's credentials, so every checkout and worktree starts and stops the same tunnel.
DEVICE_CONFIG="$HOME/.cloudflared/$DEVICE_TUNNEL.yml"
LEGACY_DEVICE_CONFIG="$STATE_DIR/cloudflared-device.yml"
# The Gradle user home's gradle.properties, which Gradle reads before local.properties (NativeOAuthConventionPlugin).
USER_PROPS="${GRADLE_USER_HOME:-$HOME/.gradle}/gradle.properties"
DEVICE_METRICS_PORT=20249
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

# ---------------------------------------------------------------- mode

# The device host (bare, no scheme), or nothing in localhost mode. Looked up as Gradle does for the apps: the Gradle user
# home's gradle.properties wins when it has the key (even empty), else local.properties. PLC and the AppView sit on siblings
# one level below the same zone (Cloudflare's free certificate covers only *.zone): dev.example.com -> plc-dev.example.com.
# NativeOAuthConventionPlugin derives the same names for the apps.
device_host() {
  local h
  if has_user_property "$DEVICE_HOST_KEY"; then h=$(user_property "$DEVICE_HOST_KEY"); else h=$(property "$DEVICE_HOST_KEY"); fi
  h=${h#https://}
  echo "${h%/}"
}

# Where the device host comes from, for messages.
device_host_source() {
  if has_user_property "$DEVICE_HOST_KEY"; then echo "${USER_PROPS/#$HOME/~}"; else echo "local.properties"; fi
}
sibling() { echo "$1-$2"; }

env_value() { sed -nE "s/^$1=(.*)$/\1/p" docker/.env | tail -1; }

# Sets KEY=VALUE in docker/.env, replacing an existing line.
set_env() {
  if grep -q "^$1=" docker/.env; then
    sed -i.bak "s|^$1=.*|$1=$2|" docker/.env && rm docker/.env.bak
  else
    printf '\n%s=%s\n' "$1" "$2" >>docker/.env
  fi
}

# The PDS hostname the shared stack runs under (empty for localhost). Every checkout drives the same compose project, so
# the PDS container says which hostname its volumes were made under; this checkout's docker/.env may be a stale copy, and
# is only the fallback when the container is gone.
stack_hostname() {
  local id h
  id=$("${COMPOSE[@]}" ps -aq pds 2>/dev/null | head -1 || true)
  if [[ -n $id ]]; then
    h=$(docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$id" 2>/dev/null | sed -n 's/^PDS_HOSTNAME=//p' | tail -1)
    [[ $h == localhost ]] && h=""
    echo "$h"
  else
    env_value PDS_HOSTNAME
  fi
}

# Accounts' DID documents, the relay's host table and Jetstream's cursor all embed the PDS hostname, so volumes made
# under one hostname can't run under another. stack_hostname says which one the volumes were made under.
# On a mismatch, refuse unless --reset, which deletes the volumes and the DIDs docker/.env remembers from them; the rest
# of `up` then recreates the accounts.
ensure_mode() {
  step "Mode"
  if [[ ! -f docker/.env ]]; then
    cp docker/.env.example docker/.env
    ok "created docker/.env from .env.example"
  fi
  local want current volumes answer key
  want=$(device_host)
  current=$(stack_hostname)
  volumes=$(docker volume ls -q --filter "name=^activity-tracker_pds-data$" 2>/dev/null || true)
  if [[ ${WIPE:-0} == 1 ]]; then
    if [[ ${YES:-0} != 1 ]]; then
      [[ -t 0 ]] || die "--reset deletes every local account and record; add --yes to confirm non-interactively"
      read -r -p "    Delete the local stack's volumes (every account, record and the AppView DB)? [y/N] " answer
      [[ $answer == [yY]* ]] || die "not reset"
    fi
    stop_appview
    "${COMPOSE[@]}" down -v --remove-orphans
    for key in PDS_LEXICON_AUTHORITY_DID OZONE_SERVER_DID APPVIEW_TRIAGE_DID APPVIEW_TRIAGE_PASSWORD SEGMENT_IMPORTER_DID; do
      set_env "$key" ""
    done
    RESTART_APPVIEW=1
    ok "volumes deleted"
  elif [[ -n $volumes && $current != "$want" ]]; then
    die "the stack's accounts were created for ${current:-localhost}, but $(device_host_source) asks for ${want:-localhost}
      ($DEVICE_HOST_KEY), and their DID documents would point at the wrong PDS. Rerun with --reset to delete the
      local data and recreate it (add --seed NAME for test accounts), or change $DEVICE_HOST_KEY back."
  fi
  if [[ -n $want ]]; then
    set_env PDS_HOSTNAME "$want"
    set_env RELAY_CRAWL_HOST "$want"
    set_env PDS_INVITE_REQUIRED true
    ok "device mode: PDS https://$want, PLC https://$(sibling plc "$want"), AppView https://$(sibling appview "$want") (from $(device_host_source))"
  else
    set_env PDS_HOSTNAME ""
    set_env RELAY_CRAWL_HOST ""
    set_env PDS_INVITE_REQUIRED ""
    ok "localhost mode"
    warn "physical phones need device mode: set $DEVICE_HOST_KEY in ${USER_PROPS/#$HOME/~} and run scripts/dev.sh device-setup (README)"
  fi
}

# ---------------------------------------------------------------- docker

ensure_docker_running() {
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
}

ensure_docker() {
  step "Docker stack"
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

# ---------------------------------------------------------------- labeler

# Ozone and its account moderation.test, with mod.test (admin) and triage.test (the AppView's read-only member) on its team
# (docker/scripts/setup-labeler.sh). Its DID only exists once the PDS runs, so the first run writes it to docker/.env (where the
# AppView's run task reads it, with triage.test's app password) and to local.properties (which the clients are built with); a
# changed DID restarts the AppView.
ensure_labeler() {
  step "Labeler (Ozone)"
  local did previous
  previous=$(sed -nE 's/^OZONE_SERVER_DID=(.*)$/\1/p' docker/.env)
  did=$(docker/scripts/setup-labeler.sh 2> >(grep -v '^ Container ' | sed 's/^/    /' >&2)) || die "labeler setup failed"
  [[ $did != "$previous" ]] && RESTART_APPVIEW=1
  ok "moderation.test ($did), Ozone http://localhost:3010"
  if [[ $(property "$LABELER_PROP_KEY") != "$did" ]]; then
    set_property "$LABELER_PROP_KEY" "$did"
    LABELER_CHANGED=1
    ok "$LABELER_PROP_KEY=$did"
  fi
}

# ---------------------------------------------------------------- AppView signing key

# The AppView's P-256 key (APPVIEW_SIGNING_KEY in docker/.env), which it publishes as #atproto and signs Spaces
# delegation tokens with to sync athletes' tier spaces. Generated once; the AppView's run task reads it from
# docker/.env. Rotating it is harmless locally: PDSes resolve the key from the AppView's DID document each time.
ensure_signing_key() {
  step "AppView signing key"
  if grep -qE '^APPVIEW_SIGNING_KEY=[0-9a-fA-F]{64}$' docker/.env; then
    ok "APPVIEW_SIGNING_KEY set"
    return
  fi
  local key
  # The private scalar from openssl's text dump: hex pairs after "priv:", left-padded to 32 bytes.
  key=$(openssl ecparam -name prime256v1 -genkey -noout | openssl ec -text -noout 2>/dev/null |
    sed -n '/^priv:/,/^pub:/p' | grep -v -e '^priv:' -e '^pub:' | tr -d ' :\n')
  key=$(printf '%064s' "${key: -64}" | tr ' ' 0)
  [[ $key =~ ^[0-9a-f]{64}$ ]] || die "couldn't generate a P-256 key with openssl"
  if grep -q '^APPVIEW_SIGNING_KEY=' docker/.env; then
    sed -i.bak "s|^APPVIEW_SIGNING_KEY=.*|APPVIEW_SIGNING_KEY=$key|" docker/.env && rm docker/.env.bak
  else
    printf '\nAPPVIEW_SIGNING_KEY=%s\n' "$key" >>docker/.env
  fi
  RESTART_APPVIEW=1
  ok "generated APPVIEW_SIGNING_KEY into docker/.env (the AppView restarts to pick it up)"
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

# Device mode's named tunnel: three stable hostnames, from the ingress file device-setup writes.
named_tunnel_pids() { pgrep -f "cloudflared tunnel --config $DEVICE_CONFIG" || true; }

# Stops the cloudflared processes matching a pattern and waits until they exit. cloudflared drains its connections for up to
# 30 s after a signal and keeps its metrics port meanwhile, so a replacement started sooner can't bind it, and the old one's
# metrics would make the new one look registered.
stop_cloudflared() {
  local pattern=$1 waited=0
  pkill -f "$pattern" || true
  while pgrep -f "$pattern" >/dev/null; do
    if ((waited >= 40)); then
      pkill -9 -f "$pattern" || true
      break
    fi
    sleep 1
    waited=$((waited + 1))
  done
}

# Moves an ingress file an older dev.sh wrote into a checkout's .dev/ to $DEVICE_CONFIG, and stops a tunnel still running
# from an old path (ensure_named_tunnel restarts it from the new one).
migrate_device_config() {
  local host=$1
  if [[ ! -f $DEVICE_CONFIG && -f $LEGACY_DEVICE_CONFIG ]] && grep -q "hostname: $host\$" "$LEGACY_DEVICE_CONFIG"; then
    mkdir -p "$(dirname "$DEVICE_CONFIG")"
    mv "$LEGACY_DEVICE_CONFIG" "$DEVICE_CONFIG"
    ok "moved $LEGACY_DEVICE_CONFIG to $DEVICE_CONFIG"
  fi
  if pgrep -f "cloudflared tunnel --config .*/cloudflared-device\.yml" >/dev/null; then
    stop_cloudflared "cloudflared tunnel --config .*/cloudflared-device\.yml"
    ok "stopped the tunnel running from an old ingress file"
  fi
}

ensure_named_tunnel() {
  step "cloudflared named tunnel"
  local host=$1 waited=0
  migrate_device_config "$host"
  [[ -f $DEVICE_CONFIG ]] && grep -q "hostname: $host\$" "$DEVICE_CONFIG" \
    || die "no tunnel config for $host; run: scripts/dev.sh device-setup"
  if [[ -n $(tunnel_pids) ]]; then
    pkill -f "cloudflared tunnel --url http://localhost:$APPVIEW_PORT" || true
    ok "stopped the quick tunnel (device mode doesn't use it)"
  fi
  if [[ -n $(named_tunnel_pids) ]] && tunnel_registered "$DEVICE_METRICS_PORT"; then
    ok "already running"
  else
    stop_cloudflared "cloudflared tunnel --config $DEVICE_CONFIG"
    mkdir -p "$STATE_DIR"
    nohup cloudflared tunnel --config "$DEVICE_CONFIG" run >"$STATE_DIR/cloudflared-device.log" 2>&1 &
    until tunnel_registered "$DEVICE_METRICS_PORT"; do
      ((waited >= 30)) && die "cloudflared didn't register a connection; see .dev/cloudflared-device.log"
      sleep 1
      waited=$((waited + 1))
    done
    ok "started (log: .dev/cloudflared-device.log)"
  fi
  TUNNEL_URL="https://$(sibling appview "$host")"
}

# The relay crawls the PDS through the tunnel (relay-crawl runs during compose up), so the tunnel starts first and this
# checks the PDS answers through it once compose is up.
check_named_tunnel() {
  local host=$1 waited=0
  until [[ $(tunnel_http_code "$host" /xrpc/_health 10) == 2* ]]; do
    ((waited >= 60)) && die "https://$host/xrpc/_health isn't reachable; see .dev/cloudflared-device.log"
    sleep 2
    waited=$((waited + 2))
  done
  ok "https://$host reaches the PDS"
}

stop_named_tunnel() {
  [[ -n $(named_tunnel_pids) ]] || return 0
  stop_cloudflared "cloudflared tunnel --config $DEVICE_CONFIG"
  ok "stopped the named tunnel"
}

# ---------------------------------------------------------------- device-setup

# One-time: log in to Cloudflare, create the named tunnel, point the three hostnames at it and write the ingress file.
# Safe to rerun; it only does what's missing.
cmd_device_setup() {
  command -v cloudflared >/dev/null || die "cloudflared not installed: brew install cloudflared"
  local host zone plc appview id creds name config
  host=$(device_host)
  [[ -n $host ]] || die "set $DEVICE_HOST_KEY in ${USER_PROPS/#$HOME/~} (every checkout) or local.properties (this one) to a subdomain of your Cloudflare zone, e.g. dev.example.com"
  [[ $host =~ ^[a-z0-9-]+(\.[a-z0-9-]+){2,}$ ]] || die "$host should be one label below a zone, e.g. dev.example.com"
  zone=${host#*.}
  plc=$(sibling plc "$host")
  appview=$(sibling appview "$host")
  step "Zone $zone"
  # Exactly one level: Cloudflare's free certificate covers *.zone, not *.dev.zone, so the host's parent must be the zone.
  [[ -n $(dig +noall +answer SOA "$zone" @1.1.1.1 | awk -v z="$zone." '$1 == z && $4 == "SOA"') ]] \
    || die "$zone isn't a DNS zone: $DEVICE_HOST_KEY must be exactly one label below the zone you added to Cloudflare"
  dig +short NS "$zone" @1.1.1.1 | grep -q '\.ns\.cloudflare\.com\.$' \
    || die "$zone isn't on Cloudflare's nameservers yet (dig NS $zone): add it to Cloudflare and switch its nameservers first"
  ok "$zone is on Cloudflare"
  step "Tunnel $DEVICE_TUNNEL"
  if [[ ! -f $HOME/.cloudflared/cert.pem ]]; then
    warn "logging in: pick $zone on the page that opens"
    cloudflared tunnel login
  fi
  id=$(cloudflared tunnel list --name "$DEVICE_TUNNEL" --output json 2>/dev/null | jq -r '.[0].id // empty')
  if [[ -z $id ]]; then
    cloudflared tunnel create "$DEVICE_TUNNEL" >/dev/null 2>&1 || die "cloudflared tunnel create failed"
    id=$(cloudflared tunnel list --name "$DEVICE_TUNNEL" --output json | jq -r '.[0].id // empty')
    [[ -n $id ]] || die "couldn't create the tunnel"
    ok "created $DEVICE_TUNNEL ($id)"
  else
    ok "$DEVICE_TUNNEL exists ($id)"
  fi
  creds="$HOME/.cloudflared/$id.json"
  [[ -f $creds ]] || die "$creds is missing (the tunnel was created elsewhere); remove it with: cloudflared tunnel delete $DEVICE_TUNNEL"
  step "DNS"
  for name in "$host" "$plc" "$appview"; do
    cloudflared tunnel route dns --overwrite-dns "$DEVICE_TUNNEL" "$name" 2>&1 | grep -v '^$' | tail -1 | sed 's/^/    /'
  done
  step "Ingress"
  mkdir -p "$(dirname "$DEVICE_CONFIG")"
  [[ -f $LEGACY_DEVICE_CONFIG ]] && rm "$LEGACY_DEVICE_CONFIG" && ok "removed the old $LEGACY_DEVICE_CONFIG"
  config="# Written by the activity-tracker repo's scripts/dev.sh device-setup from $DEVICE_HOST_KEY.
tunnel: $id
credentials-file: $creds
metrics: 127.0.0.1:$DEVICE_METRICS_PORT
ingress:
  - hostname: $host
    service: http://localhost:2583
  - hostname: $plc
    service: http://localhost:2582
  - hostname: $appview
    service: http://localhost:$APPVIEW_PORT
  - service: http_status:404"
  if [[ -f $DEVICE_CONFIG && $(cat "$DEVICE_CONFIG") == "$config" ]]; then
    ok "$DEVICE_CONFIG is current"
  else
    echo "$config" >"$DEVICE_CONFIG"
    ok "wrote $DEVICE_CONFIG"
  fi
  echo
  echo "${BOLD}Done.${RESET} Next: scripts/dev.sh --reset --seed alice (moves the stack to https://$host), then rebuild the apps."
}

# ---------------------------------------------------------------- local.properties

property() { [[ -f $PROPS ]] && grep "^$1=" "$PROPS" | tail -1 | cut -d= -f2- || true; }

# The Gradle user home's gradle.properties, which may space its separator ("key = value"), as Java properties allow.
has_user_property() { [[ -f $USER_PROPS ]] && grep -qE "^[[:space:]]*${1//./\\.}[[:space:]]*[=:]" "$USER_PROPS"; }
user_property() {
  [[ -f $USER_PROPS ]] || return 0
  sed -nE "s/^[[:space:]]*${1//./\\.}[[:space:]]*[=:][[:space:]]*//p" "$USER_PROPS" | tail -1 | sed -E 's/[[:space:]]+$//'
}
configured_url() { property "$PROP_KEY"; }

# Sets KEY=VALUE in local.properties, dropping earlier lines for KEY.
set_property() {
  local tmp
  tmp=$(mktemp)
  [[ -f $PROPS ]] && { grep -v "^$1=" "$PROPS" >"$tmp" || true; }
  echo "$1=$2" >>"$tmp"
  mv "$tmp" "$PROPS"
}

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
  set_property "$PROP_KEY" "$TUNNEL_URL"
  [[ $current != "$TUNNEL_URL" ]] && URL_CHANGED=1
  ok "$PROP_KEY=$TUNNEL_URL${current:+ (was $current)}"
}

# Device mode: the builds derive the AppView's public URL from the device host, so local.properties drops a URL left
# from a quick tunnel. Sets URL_CHANGED when that line named another URL (the installed apps were built with it).
ensure_device_properties() {
  step "local.properties"
  local current
  current=$(configured_url)
  if [[ -z $current ]] && ! grep -q "^$PROP_KEY=" "$PROPS" 2>/dev/null; then
    ok "AppView URL derived from $DEVICE_HOST_KEY: $TUNNEL_URL"
    return
  fi
  local tmp
  tmp=$(mktemp)
  { grep -v "^$PROP_KEY=" "$PROPS" || true; } >"$tmp"
  mv "$tmp" "$PROPS"
  [[ $current != "$TUNNEL_URL" ]] && URL_CHANGED=1
  ok "dropped $PROP_KEY${current:+ ($current)}: the AppView URL is derived from $DEVICE_HOST_KEY ($TUNNEL_URL)"
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
  # The redirect scheme and the labeler DID are compiled into the app, so a new tunnel URL or labeler needs a reinstall.
  if [[ ${URL_CHANGED:-0} == 1 || ${LABELER_CHANGED:-0} == 1 || ${FORCE_ANDROID:-0} == 1 ]]; then
    echo "    installing the debug app (${URL_CHANGED:+tunnel URL changed}${LABELER_CHANGED:+labeler changed}${FORCE_ANDROID:+--android})"
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
      --reset) WIPE=1 ;;
      --yes) YES=1 ;;
      *) die "unknown option: $1 (see the header of scripts/dev.sh)" ;;
    esac
    shift
  done
  ensure_docker_running
  ensure_mode
  local host
  host=$(device_host)
  [[ -n $host ]] && ensure_named_tunnel "$host"
  ensure_docker
  ensure_lexicons
  ensure_labeler
  if [[ -n $host ]]; then
    check_named_tunnel "$host"
    ensure_device_properties
  elif [[ ${NO_TUNNEL:-0} != 1 ]]; then
    stop_named_tunnel
    ensure_tunnel
    ensure_local_properties
  else
    step "cloudflared tunnel"
    warn "skipped: the PDS can't reach the AppView's Spaces checkUserAccess without it"
  fi
  ensure_signing_key
  ensure_appview
  [[ ${NO_ANDROID:-0} != 1 ]] && ensure_android
  ensure_accounts
  echo
  echo "${BOLD}Ready.${RESET} Web: ./gradlew :webApp:wasmJsBrowserDevelopmentRun → http://127.0.0.1:8080"
  if [[ ${URL_CHANGED:-0} == 1 ]]; then
    echo "${YELLOW}The tunnel URL changed: rebuild the iOS app too (its redirect scheme is compiled in).${RESET}"
  fi
  if [[ ${LABELER_CHANGED:-0} == 1 ]]; then
    echo "${YELLOW}The labeler DID changed: rebuild the apps (it is compiled in) to send reports to it.${RESET}"
  fi
}

cmd_status() {
  step "Docker"
  if docker info >/dev/null 2>&1; then
    "${COMPOSE[@]}" ps --format 'table {{.Service}}\t{{.Status}}' | sed 's/^/    /'
  else
    warn "Docker isn't running"
  fi
  step "Labeler"
  if [[ $(http_code "http://localhost:3010/xrpc/_health") == 2* ]]; then
    ok "Ozone http://localhost:3010; $LABELER_PROP_KEY=$(property "$LABELER_PROP_KEY")"
  else
    warn "Ozone not running (scripts/dev.sh sets it up)"
  fi
  step "Tunnel"
  local host device
  device=$(device_host)
  host=$(running_tunnel_host)
  if [[ -n $device ]]; then
    if [[ -n $(named_tunnel_pids) ]] && tunnel_registered "$DEVICE_METRICS_PORT"; then
      ok "named tunnel $DEVICE_TUNNEL: https://$device, https://$(sibling plc "$device"), https://$(sibling appview "$device")"
    else
      warn "device mode ($device), but the named tunnel isn't running (scripts/dev.sh starts it)"
    fi
  elif [[ -z $host ]]; then
    warn "not running (the PDS can't reach the AppView's Spaces checkUserAccess without it)"
  elif tunnel_alive "$host"; then
    ok "https://$host"
  else
    warn "https://$host (not reachable)"
  fi
  step "local.properties"
  local url
  url=$(configured_url)
  if [[ -n $device ]]; then
    ok "device mode, $DEVICE_HOST_KEY=$device from $(device_host_source); AppView URL https://$(sibling appview "$device")"
    [[ -n $url ]] && warn "$PROP_KEY=$url is ignored in device mode (scripts/dev.sh drops it)"
  elif [[ -z $url ]]; then
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
  elif [[ -z $(named_tunnel_pids) ]]; then
    ok "not running"
  fi
  stop_named_tunnel
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
  device-setup) cmd_device_setup ;;
  -h | --help) sed -n '2,21p' "$0" | sed 's/^# \{0,1\}//' ;;
  -*) cmd_up "$@" ;;
  *) die "unknown command: $1 (up, status, down, device-setup)" ;;
esac
