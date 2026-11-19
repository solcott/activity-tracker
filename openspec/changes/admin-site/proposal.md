# Proposal

## Why

Operators have no way to see or steer the AppView: workers fail only into the logs, queues and cursors can be read only with `psql`, and fixes such as rematching ship as Flyway migrations. Moderation already has its tool (the Ozone labeler), but running the AppView doesn't. Before any operator page can be built, the AppView needs a secure place to put one: a sign-in, a notion of who is an operator, a record of what they did, and a firm rule that the site never becomes a back door to athletes' private data.

## What Changes

- The AppView serves a server-rendered admin web site under `/admin`, used from any browser with no client release.
- Admins sign in with their atproto account through OAuth, asking for identity only. The AppView keeps only the verified DID, gives up the tokens at once, and issues its own short-lived admin session.
- A new setting, `APPVIEW_ADMINS`, lists admin DIDs, each with a role: `viewer` (read-only) or `operator` (can also run actions). With no admins configured the site doesn't exist (`404`). Roles are read from configuration on every request, so removing someone takes effect at their next request. Access never depends on the Ozone labeler being up.
- Every operator action is recorded in an audit log (who, what, on what, outcome, when), kept at least a year and shown on its own page.
- The site is hardened as a browser surface of its own: no cross-origin access, a strict content security policy, a same-site session cookie, and CSRF protection on every action.
- Admin pages show identifiers, statuses, counts and times, never private record content. Non-public content stays behind `moderation.getSubject`'s report-gated, audited flow.
- The site is a shell other changes add pages to (health, job queues, accounts, segments), each in its own files.
- The local stack creates an admin account and configures the AppView with it, so the site works after `scripts/dev.sh` with no manual steps.

## Capabilities

### New Capabilities
- `admin/admin-site`: the AppView's operator web site: who may use it and with what role, how they sign in, sessions and their protection, the audit log of actions, the rule that admin pages reveal no private content, and how operator pages plug in.

### Modified Capabilities
- `dev/local-stack`: bringing up the stack also creates a local admin account and configures the AppView's admin list with it.

## Impact

- **AppView** (`server/appview`): a new `admin` package (routes, pages, sessions, audit); `AppViewConfig` gains `APPVIEW_ADMINS`; `Application.kt` mounts `/admin` outside the any-host CORS rule; new tables `admin_sessions` and `admin_actions` (Flyway migration); the AppView serves a second OAuth client metadata document for the admin site.
- **Reuse:** `core/atproto`'s `OAuthClient` (already an AppView dependency) does PAR, PKCE, DPoP and the identity check; no OAuth code is written again.
- **Dependencies:** `ktor-server-html-builder` (kotlinx.html) and htmx (its WebJar) served as a pinned static file from the AppView (no CDN), all through `gradle/libs.versions.toml`.
- **Local stack:** `docker/scripts` (a `siteadmin.test` account), `scripts/dev.sh`, `docker/.env.example`.
- **Follow-on changes** build on this: `admin-health`, `admin-job-queues`, `admin-accounts`, `admin-segments`.
- No lexicon, client or iOS change.
