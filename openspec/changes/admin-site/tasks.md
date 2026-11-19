# Tasks

## 1. Configuration and storage

- [x] 1.1 Parse `APPVIEW_ADMINS` (`did=viewer|operator`, comma-separated) into `AppViewConfig.admins`, failing start-up on a malformed entry with its text; verify with `AppViewConfig` unit tests for valid, empty, missing and malformed lists
- [x] 1.2 Add the next Flyway migration creating `admin_sessions(token_hash, did, csrf_token, created_at, expires_at)` and `admin_actions(id, admin_did, action, target, params, outcome, at)` with indexes on `at` and `(admin_did, at)`, H2-portable (BIGINT millis, text JSON), plus their Exposed tables in `db/Tables.kt`; verify `MigrationVersionsTest` and `:server-appview:test` pass

## 2. CORS and the admin route shell

- [x] 2.1 Move CORS from application-wide to route-scoped installs on the XRPC, well-known and `/img` groups in `Application.kt`/`Routes.kt`; verify with tests that an XRPC route still answers a preflight with `Access-Control-Allow-Origin` and an `/admin` path sends none
- [x] 2.2 Add `ktor-server-html-builder` and the htmx WebJar to `gradle/libs.versions.toml` and `server/appview`; serve htmx and one site stylesheet under `/admin/static/` with versioned paths; verify a test fetches both with the right content types
- [x] 2.3 Add the `admin` package: `AdminPage`, `AdminContext`, `adminLayout` (navigation from the page list, signed-in DID and role, sign-out, CSRF meta tag and `hx-headers`, htmx config without injected styles) and the security headers (CSP, `X-Frame-Options`, `Referrer-Policy`, `Cache-Control: no-store`) on every `/admin` response; mount nothing when `admins` is empty; verify with tests for the headers and for `404` with no admins

## 3. Sign-in and sessions

- [x] 3.1 Serve `/admin/oauth/client-metadata.json` for an https public URL, and use the atproto loopback client id with a `127.0.0.1` redirect on a loopback host (redirecting `/admin` on `localhost` to `127.0.0.1`); verify with tests of both client configurations and the metadata document's fields
- [x] 3.2 Implement `POST /admin/signin` and `GET /admin/oauth/callback` over `core/atproto`'s `OAuthClient` with the guarded identity resolver: refuse non-admins before redirecting, hold pending authorizations in a 10-minute Caffeine cache bound to an `admin_signin` cookie, re-check the DID after `completeAuthorization`, revoke the tokens and store none; verify with test-host tests using a fake `AdminOAuth` (the OAuth protocol itself is covered by `core/atproto`'s tests) for success, non-admin, state mismatch, replay in another browser and expired pending sign-in
- [x] 3.3 Implement admin sessions: 32-byte cookie (`HttpOnly`, `SameSite=Strict`, path `/admin`, `Secure` off only on loopback), SHA-256 stored, fixed 12-hour lifetime, sign-out deletes the row, role re-read from config on every request; verify with tests for expiry, sign-out, restart survival (new application over the same database), removed admin and lowered role

## 4. Actions, audit and the audit page

- [x] 4.1 Implement `adminAction(name)`: POST only, `403` for viewers, CSRF token and `Origin` check, audit row written in `finally` with `ok`, `refused` or `failed: <error class>`; verify with tests that a viewer, a missing token and a foreign origin are refused and recorded, and a throwing action is recorded as failed
- [x] 4.2 Add the daily prune of `admin_actions` older than 13 months and of expired `admin_sessions`, alongside the moderation access log prune; verify with a test using a fixed clock
- [x] 4.3 Add the home page and `AuditPage` (newest first, keyset paged, filters by admin, action and target); verify with tests that a viewer can read it and the filters work
- [x] 4.4 Add the privacy test harness: seed an athlete with non-public activities, comments and private details carrying a marker string, render every registered `AdminPage` and its fragments, and assert the marker never appears; verify it fails when a page is made to print a record's title, then passes again

## 5. Local stack and docs

- [ ] 5.1 Add `docker/scripts/setup-admin.sh` (create `siteadmin.test`, write `APPVIEW_ADMINS=<did>=operator` to `docker/.env`), run it from `scripts/dev.sh` and after a wipe, add an empty `APPVIEW_ADMINS` to `.env.example`, and restart the AppView when the DID changes; verify by running `scripts/dev.sh` twice and once with `--reset` and checking `docker/.env`
- [x] 5.2 Document the admin site in `.agents/rules/appview.md` (admin list, sign-in, `adminAction`, `AdminPage`, privacy rule), `.agents/rules/docker.md` (the admin account) and the `local-stack` skill (signing in at `/admin`); verify `scripts/check-agent-sync.sh` passes

## 6. Integration check

- [x] 6.1 Run `./gradlew :server-appview:test` and `./gradlew check` (through the `gradle-runner` agent) and confirm both pass
- [x] 6.2 With `scripts/dev.sh` running, sign in as `siteadmin.test` at the AppView's `/admin` in the browser pane, open the audit log, sign out, and confirm a non-admin account is refused; repeat the sign-in in device mode when a device host is configured

## Workflow follow-up

- Archive the change with `/opsx:archive` once implemented and reviewed, so `admin/admin-site` and the `dev/local-stack` delta reach the main specs before the follow-on admin page changes archive.
