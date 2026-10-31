# Design

## Context

- The pinned Spaces-alpha PDS derives everything from `PDS_HOSTNAME` (`packages/pds/dist/config/config.js`). For `localhost` its public URL is `http://localhost:<port>`; for any other hostname it is `https://<hostname>`, with no port. The service DID is `did:web:<hostname>` and the OAuth issuer is the public URL. So the PDS needs a hostname with valid TLS on port 443 that never changes while its accounts exist.
- Clients take `PLC_URL` and `HANDLE_RESOLVER_URL` from constants in `NetworkConfigs`. Native builds already read one URL from `local.properties` (`activitytracker.appviewPublicUrl`), which `NativeOAuthConventionPlugin` generates into `GeneratedNativeNetworkConfig`, and the AppView's `run` task reads the same property.
- `scripts/dev.sh` runs a quick tunnel to the AppView only. Its hostname changes on every run, which forces native rebuilds and recreating Spaces (the managing app's `did:web` changes).
- The relay, Jetstream and Ozone share the PDS's network namespace and are patched to reach a plain-HTTP `localhost` PDS (`.agents/rules/docker.md`). `relay-crawl` registers `localhost:2583`.
- Tracked files must not name the maintainer's domain (`dev/public-repository`).

## Goals / Non-Goals

**Goals:**
- One-time setup, then the same `scripts/dev.sh` loop as today, with the device mode switched on by `local.properties`.
- The device works off the LAN too (cellular), so background uploads can be tested realistically.

**Non-Goals:**
- Running the stack without a Cloudflare-hosted domain (see the alternatives under D1).
- Keeping data across mode switches, or running both modes at once.
- Exposing Ozone, the relay or Jetstream publicly; only the clients' three endpoints are exposed.
- Production configuration of the clients (still a separate concern).

## Decisions

### D1. A named Cloudflare tunnel on the developer's domain
`cloudflared tunnel run` with a locally generated ingress config maps three hostnames to `localhost:2583` (PDS), `localhost:2582` (PLC) and `localhost:8081` (AppView). It gives stable names, real certificates and reachability from anywhere. It's free once the domain's nameservers are on Cloudflare, and it needs no software on the device.

Alternatives: **Tailscale Funnel** gives one hostname and three ports. That fits a PDS on 443 and PLC on 8443, but the AppView would need a third port or stay on the quick tunnel, and containers would hairpin to a tailnet name. **A LAN hostname with mkcert** needs a CA profile trusted on every device, works on the same Wi-Fi only, and runs into the PDS's guards against private addresses. Both stay possible later, because nothing below depends on Cloudflare except `dev.sh`'s tunnel step.

### D2. One configured host; siblings derived from it
Each developer sets one value, `activitytracker.deviceHost` in `local.properties`: a subdomain of their own Cloudflare zone, for example `dev.example.com`. The repository has no default and names no real domain. The PDS takes that host. PLC and the AppView take siblings made by prefixing its first label: `plc-dev.example.com` and `appview-dev.example.com`.

The siblings aren't `plc.dev.example.com` because Cloudflare's free Universal SSL certificate covers the apex and `*.<zone>` only, not `*.*.<zone>`. Names under `dev.<zone>` would need Advanced Certificate Manager (paid), and delegating `dev.<zone>` as its own zone needs Enterprise. `device-setup` rejects a host that isn't exactly one label below a zone in the logged-in account, and says why.

The PDS gets the configured name itself because it appears in DID documents and on the consent screen. One shared host can't serve all three: the PDS and the AppView both serve `/xrpc` and `/.well-known/did.json`, and their `did:web`s would collide.

### D3. How the mode turns on
Device mode is on when `activitytracker.deviceHost` is set. `NativeOAuthConventionPlugin` and `dev.sh` both derive the three URLs from it with the same rule (TestKit and a shell check keep the two rules in step). `dev.sh` also writes the derived AppView URL to `activitytracker.appviewPublicUrl`, as it does with today's quick-tunnel URL, so the AppView's `run` task and the redirect scheme need no change. `dev.sh` then writes `PDS_HOSTNAME` and `RELAY_CRAWL_HOST` into `docker/.env`, and compose reads them as `${PDS_HOSTNAME:-localhost}` and `${RELAY_CRAWL_HOST:-localhost:2583}`. The tunnel name is fixed (`activitytracker-dev`). Its credentials stay in `~/.cloudflared`, and the ingress file goes in `.dev/`. A property was chosen over a `--device` flag because the hostname is baked into data: a per-run flag would make it too easy to start the wrong mode against existing accounts.

### D4. Clients read PLC and handle-resolver URLs from generated config
`NativeOAuthConventionPlugin` adds `PLC_URL` and `HANDLE_RESOLVER_URL` to `GeneratedNativeNetworkConfig`, derived from `deviceHost` (D2), defaulting to `http://localhost:2582` and `http://localhost:2583`. `NetworkConfigs.native()` uses them. Web and desktop keep the localhost constants: they run on the Mac, resolve DIDs through local PLC (same data), and then follow the DID document to the public PDS. Simulator and Android builds made in device mode use the public URLs too, so they keep working without `adb reverse`.

### D5. Containers hairpin through Cloudflare
In device mode, DID documents name `https://<deviceHost>`. The relay, Jetstream and the AppView follow them out through Cloudflare and back in through the tunnel. Those are public IPs with valid TLS, so the localhost SSRF patches don't apply to them and the AppView's guarded client accepts them. The patches stay in place for localhost mode. The alternative was split-horizon DNS, aliasing the hostname to the PDS container inside Docker. It was rejected because the PDS serves no TLS, so it would need a TLS-terminating proxy and a trusted certificate inside the containers. Hairpin latency is acceptable for dev.

Ozone keeps `http://localhost:3010`. Only the PDS (sharing its network namespace) and the host's AppView call it, and both still reach it.

### D6. Switching modes wipes; it doesn't migrate
Accounts' DID documents, the relay's host table and Jetstream's cursor all embed the PDS host. `dev.sh` records the hostname the volumes were made under in `docker/.env` (`PDS_HOSTNAME`) and, while the PDS volume exists, compares it with the configured mode before starting anything. That works with the containers stopped, unlike asking the running PDS. On a mismatch it exits with an explanation unless `--reset` is given. `--reset` asks for confirmation (or takes `--yes`), runs `compose down -v`, clears the DID-derived keys in `docker/.env` (`PDS_LEXICON_AUTHORITY_DID`, `OZONE_SERVER_DID`, triage credentials, `SEGMENT_IMPORTER_DID`) and continues the normal bring-up, which recreates them. Rewriting each account with PLC operations was rejected. `setup-labeler.sh` could sign them, but the relay and Jetstream state would also need surgery, and local data is throwaway.

## Risks / Trade-offs

- [The relay's handling of a public host] → Checked at the pinned indigo commit (`b2619d8`): `ParseHostname` (`cmd/relay/relay/host.go:143-189`) turns a bare non-`localhost` name into `https`, so `noSSL=false`, and rejects a port. The subscription then dials `wss` through `ssrf.PublicOnlyDialer` (`cmd/relay/relay/slurper.go:300-304`), which Cloudflare's public addresses pass. The admin `requestCrawl` uses the same parser (`cmd/relay/handlers_admin.go:494`). So `RELAY_CRAWL_HOST` is the bare `<deviceHost>`, and `RELAY_ALLOW_INSECURE_HOSTS` only affects `http` hosts; it stays on for localhost mode.
- [The tunnel or Cloudflare is down, so the stack is unusable in device mode, including from the Mac] → Unsetting `deviceHost` and running `--reset` returns to localhost mode.
- [The developer's domain becomes publicly reachable and serves a dev PDS with open sign-up] → Device mode sets `PDS_INVITE_REQUIRED=true`. `create-account.sh` uses the admin API to mint an invite code, so seeding still works. Document that the tunnel should be stopped (`dev.sh down`) when not in use.
- [Hairpinned firehose and repo fetches are slower] → Acceptable for local dev; noted in the docs.
- [The redirect scheme changes from `com.trycloudflare.*` to the reverse of the AppView host] → It is already derived from the property, so this only needs one rebuild.

## Migration Plan

1. A developer moves their domain's nameservers to Cloudflare, sets `activitytracker.deviceHost` and runs `scripts/dev.sh device-setup` once.
2. `scripts/dev.sh --reset --seed alice` wipes the localhost-mode data and brings the stack up in device mode.
3. Rebuild the native apps.

Rollback: remove `activitytracker.deviceHost` from `local.properties`, restore a quick-tunnel URL (or let `dev.sh` write one), run `scripts/dev.sh --reset`, and rebuild.
