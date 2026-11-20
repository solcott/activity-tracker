# Design

## Context

See proposal.md for why. What shapes the approach:

- The AppView (`server/appview`) is Ktor 3 on Netty. Every route today is XRPC (service-auth JWTs with a matching `lxm`), JSON well-known documents, or `/img` redirects. There is no HTML, no cookie and no session handling. CORS is installed application-wide with `anyHost()` (`Application.kt`).
- `server/appview` already depends on `core/atproto`, whose `OAuthClient` does handle/DID resolution, PAR, PKCE (S256), DPoP with nonce retry, the RFC 9207 issuer check and the "authorization server is authoritative for `sub`" check, and can `revoke`. `Application.kt` already builds an `IdentityResolver` over the guarded (SSRF-safe) HTTP client for untrusted identities.
- Two privileged-caller patterns exist: one DID from configuration (`SEGMENT_IMPORTER_DID`), and Ozone team roles (`ModeratorSubjects`). The user chose a configuration list, so admin access never depends on Ozone.
- The AppView's database holds private data (`.agents/rules/appview.md`). Moderator access to it is already narrow, report-gated and audited (`moderation/private-subjects`, `ModerationAccessLog`), and the admin site must not widen it.
- Configuration comes from environment variables; the local stack writes them to `docker/.env`, which the AppView's run task and `scripts/run-appview.sh` read (as for `APPVIEW_TRIAGE_*`).

## Goals / Non-Goals

**Goals:**
- One secure shell the four follow-on changes add pages to with no edits to each other's files.
- Sign-in reusing `OAuthClient` unchanged.
- An action helper that makes role checks, CSRF and audit impossible to forget.

**Non-Goals:**
- Any operator page beyond the audit log (follow-on changes).
- Moderation features: reports, labels and takedowns stay in Ozone; non-public content stays behind `moderation.getSubject`.
- Changing admins at runtime from the site. The list is configuration; a change takes a restart.
- Running more than one AppView instance (sign-ins in progress are held in memory; see Risks).

## Decisions

### Server-rendered HTML with htmx, served by the AppView
Pages are built with kotlinx.html (`ktor-server-html-builder`) and made interactive with htmx (partial refresh, polling a status panel, posting actions without a full reload). htmx comes from its npm WebJar (`org.webjars.npm:htmx.org`) through `gradle/libs.versions.toml` and is served from the classpath under `/admin/static/`, so nothing loads from a CDN.

Alternatives: a Compose/wasm admin app (reuses the design system, but needs browser auth to the AppView anyway, a second build, and ships far more code for a few tables); admin screens inside the client apps like the Moderation page (ships with releases, three modules per screen, and puts operator code in every user's app); a separate admin service reading the database (a second deployable with the same database credentials and none of the AppView's in-process state such as worker status). Server-rendered pages keep everything in one process, close to the state they show, with the smallest attack surface.

### Admin list in configuration
`APPVIEW_ADMINS` is a comma-separated list of `did=role` entries, `role` being `viewer` or `operator`, parsed in `AppViewConfig` into a `Map<Did, AdminRole>`. A malformed entry fails start-up naming the entry (an operator who mistypes it should find out now, not when locked out). An empty or missing list mounts no `/admin` routes at all, so the paths answer the server's ordinary `404`.

Alternative: Ozone team roles, rejected by the user because the dashboard must work when Ozone is down and moderating trust isn't operating trust.

### Sign-in: OAuth for identity, then the AppView's own session
- **Client id.** With a public `APPVIEW_PUBLIC_URL` (https), the AppView serves `/admin/oauth/client-metadata.json` (`application_type: web`, `token_endpoint_auth_method: none`, `dpop_bound_access_tokens: true`, scope `atproto`, redirect `<publicUrl>/admin/oauth/callback`), separate from the apps' `/oauth/client-metadata.json` so the two clients' scopes and redirects never mix. On a loopback development host it uses an atproto loopback client id (`http://localhost?redirect_uri=http://127.0.0.1:<port>/admin/oauth/callback&scope=atproto`), as the web app does, and `/admin` on `localhost` redirects to `127.0.0.1` first so the cookies set at the start of a sign-in come back with the callback.
- **Flow.** `POST /admin/signin` with a handle or DID calls `OAuthClient.startAuthorization` (built over the guarded identity resolver and HTTP client, since the identifier is untrusted). If the resolved DID isn't a configured admin it stops there, before any redirect. The `PendingAuthorization` is held in memory (Caffeine, 10-minute expiry) keyed by its `state`, and the browser gets a short-lived `admin_signin` cookie holding a random value whose hash is stored with it, so a callback URL replayed in another browser fails. `GET /admin/oauth/callback` takes the pending entry once, checks the cookie, calls `completeAuthorization` (which checks state, issuer and `sub`), re-checks the DID is an admin, calls `revoke` on the tokens (best effort: a failed revoke is logged, and the tokens are only ever held in memory for this request), and creates a session.
- **Why scope `atproto` only.** It proves identity and grants nothing on the account, so even a leaked token in that brief window can't write to the admin's repo.

Alternative: app passwords typed into the admin site (the AppView would handle a credential, which this project never does), or a service-auth JWT pasted from a client (awkward, and the client would need an admin feature).

### Sessions stored hashed in Postgres
`admin_sessions(token_hash PK, did, csrf_token, created_at, expires_at)`. The cookie (`admin_session`, path `/admin`, `HttpOnly`, `SameSite=Strict`, `Secure` unless the host is loopback) holds 32 random bytes; the table holds their SHA-256, so a database read can't be replayed as a session. Lifetime is a fixed 12 hours, no sliding renewal. Sign-out deletes the row. Expired rows are deleted by the same daily prune as the audit log. Stored in the database rather than memory so restarts don't sign operators out mid-incident. This is small enough that Ktor's Sessions plugin adds nothing; a few lines read the cookie and look the row up in an `AdminSession` route plugin.

### One action helper, one page registry
- `AdminPage` (`path`, `title`, `fun Route.install(ctx: AdminContext)`) is the unit a follow-on change adds; `adminRoutes(pages: List<AdminPage>)` mounts them under the authenticated route and builds the navigation from the list. Pages render through a shared `adminLayout(title) { … }` that draws navigation, the signed-in DID and role, sign-out, and the CSRF meta tag. Follow-on changes add a page class and one line to the list in the AppView's wiring.
- `adminAction(name) { call -> ActionResult }` is the only way to register a POST: it checks the role (`403` for a viewer), checks the CSRF token, runs the body, and writes the `admin_actions` row in a `finally` with the outcome (`ok`, `refused`, or `failed: <error class>`), so no action can skip the audit. Targets and parameters are recorded as given by the form, which by the privacy rule hold only identifiers and settings.

### CSRF: synchronizer token plus origin check
Each session row carries a random `csrf_token`. The layout puts it in a `<meta>` tag and in htmx's `hx-headers` on `<body>` (`X-CSRF-Token`); plain forms carry it as a hidden field. `adminAction` requires it to match, and also refuses a POST whose `Origin` header is present and isn't the AppView's own. `SameSite=Strict` is the first line; the token covers older browsers and same-site subdomains.

### CORS and security headers
CORS moves from application-wide to the route groups that need it (XRPC, well-known, `/img`), using Ktor 3's route-scoped `install(CORS)`, so `/admin` never sends `Access-Control-Allow-Origin`. Every `/admin` response sends `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; connect-src 'self'; frame-ancestors 'none'; form-action 'self' https: http://127.0.0.1:* http://localhost:*; base-uri 'none'; object-src 'none'` (sign-in's form posts to the AppView, which redirects to the account's authorization server, and browsers check `form-action` against that redirect too), `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: same-origin` (not `no-referrer`, under which Chrome posts forms with `Origin: null`, which the origin check refuses) and `Cache-Control: no-store`. htmx is configured (through its `htmx-config` meta tag) not to inject its indicator `<style>`, so `style-src 'self'` holds; the site's CSS is one static file.

### The privacy rule, enforced by construction and by test
Admin pages read through queries written for them that select identifiers, statuses, counts and times, never a record JSON column, blob or track data. The spec requirement is backed by a test each follow-on change extends: it seeds an athlete with non-public activities, comments and private details carrying a distinctive marker string, renders every registered `AdminPage` (and its htmx fragments) for that athlete, and asserts the marker never appears.

### Audit log
`admin_actions(id, admin_did, action, target, params, outcome, at)`, `params` as JSON text (portable to H2), indexed on `at` and `(admin_did, at)`. Pruned daily after 13 months, in the same loop as the moderation access log prune. Its page (`AuditPage`) pages by `(at, id)` keyset like other AppView lists and filters by admin, action and target.

### Local stack
`docker/scripts/setup-admin.sh` (run by `scripts/dev.sh`, safe to rerun, recreated on wipe like `setup-importer.sh`) creates `siteadmin.test` and writes `APPVIEW_ADMINS=<its DID>=operator` to `docker/.env`, with an empty `APPVIEW_ADMINS` added to `.env.example`. A changed DID restarts the AppView as the importer's does. The `local-stack` skill gains the admin sign-in check.

## Risks / Trade-offs

- [Admin's PDS or account compromised gives admin access] → Accepted: it is the admin's own identity, as for moderators. Operators should use accounts on a PDS they trust; the audit log shows what was done. Sessions are short and not renewable.
- [Sign-ins in progress live in one process's memory] → A restart during a sign-in makes it fail with a clear "start again"; more than one instance would need the pending table in Postgres. Noted for when the AppView scales out.
- [Moving CORS to route groups could drop a header a client relies on] → The AppView tests that check CORS on XRPC routes keep passing; add one test that `/admin` sends none and one that an XRPC route still does.
- [htmx WebJar version drift] → Pinned in the version catalog; the served path includes the version, so a bump busts caches.
- [Operators could paste private content into an action's parameters, landing it in the audit log] → Action forms take identifiers and enumerated settings only; free-text fields are limited to an optional short note, and the privacy test covers the audit page.

## Migration Plan

One Flyway migration adds `admin_sessions` and `admin_actions`. Deploying with `APPVIEW_ADMINS` unset changes nothing visible but the CORS move. Rollback is redeploying the previous build; the two tables are unused by it.
