# Design

## Context

- **Read predicates.** Every tier decision goes through `visibleTo` / `admits` (`api/TierAccess.kt`). Feeds also apply `shownInFeeds()` (`Views.kt`). `user-blocking` adds a block term to `admits`.
- **Media.** All media goes through `/img/avatar` and `/img/media` (`api/BlobRedirects.kt`). Public photos redirect to the PDS, and non-public ones are served with short-lived signed URLs (`PrivateMediaTokens`).
- **Account events.** `Indexer` already receives Jetstream `#account` events but acts only on `status == deleted`.
- **Auth.** The AppView verifies ES256K and ES256 service auth with BouncyCastle (`ServiceAuthVerifier`). It holds its own `did:web` signing key (`APPVIEW_SIGNING_KEY`).
- **Dependencies.** The AppView has no CBOR dependency today.
- **Client hard rule.** Clients have no atproto SDK. Every XRPC call is hand-written on Ktor in `core/atproto`. That rule covers client code only; Ozone runs as a separate server.

## Goals / Non-Goals

**Goals:**
- Use atproto's standard moderation surface (reports, labels, Ozone) unchanged, so later tooling and other labelers fit.
- Enforce labels at the same few chokepoints as visibility, not per screen.
- Give moderators access to reported private content without giving them general access to private content.

**Non-Goals:**
- Takedowns at the PDS. Our Ozone isn't an admin of athletes' PDSes, so a takedown here hides content in this app only.
- Viewer label preferences, third-party labelers, automated classifiers (BACKLOG.md) and video.

## Decisions

### Ozone as the labeler, unchanged
- Run the upstream Ozone image, pinned by digest like the PDS. It gets its own Postgres database (a second database on `appview-db` is fine locally).
- Its service DID is a `did:plc` account (locally `moderation.test` on the local PDS). Its DID document gets `#atproto_labeler` (the Ozone URL) and `#atproto_label` (Ozone's signing key) through a PLC operation in a `docker/scripts/setup-labeler.sh` step called from `scripts/dev.sh`.
- Team members are added through Ozone's own team tools. The AppView's `did:web` is added with the `triage` role.
- *Alternative:* moderation tables and an admin API inside the AppView. Rejected: we'd rebuild Ozone's queue, history, team roles and UI.

### Reports: one hand-written procedure plus a proxy header
- Add `CreateReport` to `core/lexicon`, with the `strongRef` / `repoRef` subject union and reason types.
- Reason mapping:

  | Sheet reason | Reason type |
  |---|---|
  | spam | `reasonSpam` |
  | sexual | `reasonSexual` |
  | violence or graphic | `reasonViolation` |
  | harassment | `reasonRude` |
  | misleading or impersonation | `reasonMisleading` |
  | other | `reasonOther` |

- `XrpcClient` gains an optional per-call header map, so the PDS call can carry `atproto-proxy: <labelerDid>#atproto_labeler`. The PDS mints the service auth to the labeler itself.
- The labeler DID comes from `AtprotoConfig`, the same way the AppView's DID does (`local.properties` locally).

### Labels: subscribeLabels with a server-side CBOR decoder
- The AppView opens `com.atproto.label.subscribeLabels?cursor=` to each trusted labeler. Frames are a DAG-CBOR header plus body.
- Add `jackson-dataformat-cbor`, or `kotlinx-serialization-cbor` with an AppView-only decoder, to `:server-appview` only.
- Verification:
  1. Re-encode the label without `sig` as canonical DAG-CBOR (keys sorted by length, then bytewise).
  2. SHA-256 it.
  3. Verify against the labeler's `#atproto_label` multikey with the existing BouncyCastle code (`Multikey.kt`, `ServiceAuthVerifier`).
  4. Refresh the key from the DID document on failure once before dropping, which covers key rotation.
- *Alternative:* poll `com.atproto.label.queryLabels` as JSON. Rejected: it needs URI patterns and loses ordering. It stays the fallback if CBOR decoding proves painful.
- Bluesky's labeler stream carries every label it emits. Keep only labels whose `uri` is a `did:` that is an indexed athlete; drop the rest before writing.

### Label storage and effect
- `labels` table: `src`, `uri`, `val`, `cid` (nullable), `cts`, `exp` (nullable). Its primary key is (`src`, `uri`, `val`).
  - A newer `cts` replaces the row.
  - `neg` deletes it.
  - Expired rows are filtered at read time and swept periodically.
- `label_cursors` stores (`src`, `cursor`).
- Account status: an `actors.status` column, set from `#account` events. Null means active.
- Predicates:
  - **`served(viewer)`** joins into `admits`, so it covers every lookup, list and count. Its rule is: author eq viewer, or (author active and no `!takedown` on the author DID, the record URI matching its CID if pinned, or the profile).
  - **`unhidden(viewer)`** joins into list and count queries only (`shownInFeeds`, the comment and like lists, search, follow lists). Its rule is: author eq viewer, or no `!hide` on the record or the author DID. Direct lookups skip it.
- Views attach `labels: [{src, val}]` to `activityView`, `commentView` and `profileView` in one batched query per page. Lexicon `labels` fields are optional, so older clients ignore them.
- `BlobRedirects`: `isPublicActivityMedia` / `privateMedia` add the `served` check for the owning activity, and the avatar route checks the profile and account. The response is not found, so nothing reveals a takedown.

### Client treatment
- A small `ModerationDecision` in `core/data` maps a view's labels, together with its author's labels, to `Takedown` (author only), `Hidden`, `Blur(reason)`, `Warn(reason)` or `None`. Unknown values map to `None`.
- UI uses `ui/adaptive` wrappers: blurred `AsyncImage` overlays with tap-to-reveal held in presenter state for the current view only, a collapsed card for `Hidden`, and a warning chip for `Warn`.

### Moderator view of private subjects
- `app.activitytracker.moderation.getSubject` requires service auth whose `aud` is the AppView. Steps:
  1. Check the issuer's team role with `tools.ozone.team.listMembers`, cached for 5 minutes.
  2. For a non-public subject, call `tools.ozone.moderation.getSubjectStatus` (or `queryStatuses` with the subject) as the AppView. The AppView authenticates with service auth it signs with `APPVIEW_SIGNING_KEY`, addressed to the labeler with `lxm` set.
  3. Require an open review state (`reviewOpen` / `reviewEscalated`) on the subject or its author.
- The answer reuses the activity and comment view builders with viewer = the athlete, and signs photo URLs with `PrivateMediaTokens` for the moderator's DID.
- The audit log is a `moderation_access` table: `moderator_did`, `subject_uri`, `report_ref` (Ozone subject status id), `at`. A scheduled task prunes rows older than 400 days.
- The moderator screen is one more `:app-screens` / `:app-presenters` / `composeApp` screen. It sits behind a `isModerator` flag from a cached `getSubject`-style role check.
- *Alternative:* Ozone fetching our content itself. Rejected: Ozone hydrates through Bluesky's AppView API, and it can't hold space credentials.

## Risks / Trade-offs

- **Takedowns here don't remove content from the PDS or other AppViews.** Public blobs stay fetchable at their PDS URL. → Say so in moderator docs. For illegal content, escalate to the athlete's PDS host, which is out of band. Public photos are only ever linked through the AppView, so the app itself stops showing them.
- **Canonical DAG-CBOR re-encoding must match byte for byte, or every label fails verification.** → Test against labels from the real Bluesky labeler and from local Ozone, and keep a known-good fixture.
- **Ozone's moderation and team APIs for service auth from a `did:web` member are lightly documented.** → Task 1 is a spike on the local stack. It confirms Ozone accepts the AppView's `did:web` as a triage member and answers `getSubjectStatus`. If it doesn't, fall back to a configured moderator allowlist plus a report check through `queryStatuses` under a dedicated triage `did:plc` account the AppView holds a key for.
- **Ozone UI hydration of `app.activitytracker.*` records.** It expects a Bluesky AppView. → Moderators copy the subject URI into the app's Moderation screen. Deeper Ozone integration is an open question.
- **Bluesky labeler stream volume.** → Drop non-athlete subjects in memory before touching the DB.
- **Extra joins on hot queries.** → Index `labels(uri, val)` and `actors(did, status)`. `EXPLAIN` the timeline with labels present.
- **Privacy expectations.** → The privacy policy and the block and report UI copy must say moderators can view reported non-public content. Access is gated on an open report and audit-logged.

## Migration Plan

1. Flyway `V27__labels_and_account_status.sql`, which follows `user-blocking`'s V26. If `user-blocking` hasn't landed, take the next free number.
2. Deploy Ozone and create the labeler identity before enabling the subscriber. With no labeler configured, the AppView runs without labels.
3. Release the clients after the AppView serves `labels`, because older clients ignore the field. Reporting needs the labeler DID in client config.
4. Rollback: disable the subscriber in config. Labels stop applying, and stored rows are ignored.

## Open Questions

- Whether to configure Ozone's external-link setting, so a subject in the Ozone queue links straight to the app's Moderation screen. This is a convenience only.
- Whether to also publish an `app.bsky.labeler.service` record for the labeler account so Bluesky clients can see it. Our app doesn't need it.
