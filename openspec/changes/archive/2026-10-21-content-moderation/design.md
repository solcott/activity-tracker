# Design

## Context

- **Read predicates.** Every tier decision goes through `visibleTo` (`api/TierAccess.kt`). For a signed-in viewer it calls `admits`, which ends in `not(blocked(viewer, author))`. For a signed-out viewer it is only `visibility eq PUBLIC` and never reaches `admits`. Feeds also apply `shownInFeeds()` (`Views.kt`).
- **Blocking, landed.** `api/Blocks.kt` holds the pattern this change copies: correlated `exists` predicates (`blocked`, `notBlocked`, `notMuted`) that list queries add next to `visibleTo`. Their call sites are the activity feed (`Views.kt`, `notMuted`), like lists (`Views.kt`, `notBlocked`), comments (`CommentQueries.commentsVisibleTo`), search (`ActorSearch`) and follow lists (`GraphViews.followPage`). A blocked viewer's `getProfile` answers `ProfileNotFound`, and `SpaceAccess` refuses a blocked reader every tier.
- **Client menus.** Comments have a menu with Block and Mute author (`CommentMenuUi`), and profiles have one (`BlockMuteMenu` in `ProfileUi`). The activity's More menu (`ManageActivityUi`) is the owner's only, and the media gallery (`MediaGalleryUi`) has no menu. `BlockRepository.setBlocked` and the `block_confirm_*` dialog are the block flow.
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
- Predicates live in a new `api/Moderation.kt`, beside `api/Blocks.kt` and in its style:
  - **`served(viewer, author, uri, cid)`** is added to both branches of `visibleTo`, not only to `admits`, because the signed-out branch skips `admits`. That covers every lookup, list and count. Its rule is: author eq viewer, or (author active and no `!takedown` on the author DID, or on the record URI matching its CID if pinned).
  - **`unhidden(viewer, author, uri)`** sits next to `notMuted` / `notBlocked` at each list call site named in Context. Its rule is: author eq viewer, or no `!hide` on the record or the author DID. Direct lookups skip it, so counts and pages use the same filter.
  - The profile follows the block path: `getProfile` answers `ProfileNotFound` when the account is inactive or its DID or profile record carries `!takedown`, unless the viewer is the account.
  - `SpaceAccess` refuses readers other than the authority while the authority's DID carries `!takedown`, next to its block check. A takedown on one record can't reach the PDS, which only asks per space, so record takedowns rely on the AppView not serving the record.
- Views attach `labels: [{src, val}]` to `activityView`, `commentView` and `profileView` in one batched query per page. Lexicon `labels` fields are optional, so older clients ignore them.
- `BlobRedirects`: `isPublicActivityMedia` / `privateMedia` refuse media of a taken-down activity or account, and the avatar route checks the profile and account. The public routes carry no viewer, so the refusal holds for everyone, the author included; the author's client shows a placeholder from the label on the view. The response is not found, so nothing reveals a takedown.

### Client treatment
- A small `ModerationDecision` in `core/data` maps a view's labels, together with its author's labels, to `Takedown` (author only), `Hidden`, `Blur(reason)`, `Warn(reason)` or `None`. Unknown values map to `None`.
- UI uses `ui/adaptive` wrappers: blurred `AsyncImage` overlays with tap-to-reveal held in presenter state for the current view only, a collapsed card for `Hidden`, and a warning chip for `Warn`.
- Report entry points: Report joins `CommentMenuUi` and `BlockMuteMenu`. Others' activities get a small More menu in the detail top bar holding Report, next to the owner-only `ManageActivityUi`, and the gallery gets a per-photo menu. All open one report SubCircuit.
- The report sheet's block offer calls the same event as the profile's Block action, so it goes through the existing confirmation and `BlockRepository.setBlocked`. It isn't shown when the view's `viewer.blocking` is already set.

### Moderator view of private subjects
- `app.activitytracker.moderation.getSubject` requires service auth whose `aud` is the AppView. Steps:
  1. Check the issuer's team role with `tools.ozone.team.listMembers`, cached for 5 minutes.
  2. For a non-public subject, call `tools.ozone.moderation.queryStatuses` with the subject (Ozone has no `getSubjectStatus`) as the AppView's triage account, with service auth from that account's PDS addressed to the labeler with `lxm` set (see the spike result under Risks).
  3. Require an open review state (`reviewOpen` / `reviewEscalated`) on the subject or its author.
- The answer reuses the activity and comment view builders with viewer = the athlete, and signs photo URLs with `PrivateMediaTokens` for the moderator's DID. Because it reads as the athlete, a block between the moderator and the athlete doesn't hide the subject.
- The audit log is a `moderation_access` table: `moderator_did`, `subject_uri`, `report_ref` (Ozone subject status id), `at`. A scheduled task prunes rows older than 400 days.
- The moderator screen is one more `:app-screens` / `:app-presenters` / `composeApp` screen. It sits behind a `isModerator` flag from a cached `getSubject`-style role check.
- *Alternative:* Ozone fetching our content itself. Rejected: Ozone hydrates through Bluesky's AppView API, and it can't hold space credentials.

## Risks / Trade-offs

- **Takedowns here don't remove content from the PDS or other AppViews.** Public blobs stay fetchable at their PDS URL. → Say so in moderator docs. For illegal content, escalate to the athlete's PDS host, which is out of band. Public photos are only ever linked through the AppView, so the app itself stops showing them.
- **Canonical DAG-CBOR re-encoding must match byte for byte, or every label fails verification.** → Test against labels from the real Bluesky labeler and from local Ozone, and keep a known-good fixture.
- **Ozone's moderation and team APIs for service auth from a `did:web` member are lightly documented.** → Task 1 is a spike on the local stack. It confirms Ozone accepts the AppView's `did:web` as a triage member and answers `getSubjectStatus`. If it doesn't, fall back to a configured moderator allowlist plus a report check through `queryStatuses` under a dedicated triage `did:plc` account the AppView holds a key for.
  - **Spike result (task 1.2): the `did:web` path fails.** Ozone (0.4.2, image `sha256:a783cd27…`) adds the AppView's `did:web` as a `roleTriage` member, and its auth verifier lets a triage member call `tools.ozone.team.listMembers` and `tools.ozone.moderation.queryStatuses` (both `modOrAdminToken`; there is no `getSubjectStatus`, so `queryStatuses?subject=` is the status check). But it verifies service auth with `IdResolver.resolveAtprotoData`, which requires a full account document: a handle (`alsoKnownAs`) *and* an `#atproto_pds` service. Adding a handle gets past the first check, but the AppView has no PDS, so both calls answer 500 (`Could not parse pds from doc`). The fallback applies: a dedicated triage `did:plc` account, an ordinary account on a PDS (locally `triage.test`), on the team with `roleTriage`. The AppView holds an app password for it (`APPVIEW_TRIAGE_DID`, `APPVIEW_TRIAGE_PASSWORD`), signs in to its PDS and gets service auth from `com.atproto.server.getServiceAuth` (`aud` the labeler, `lxm` the method), which Ozone accepts for `listMembers` and `queryStatuses`. Chosen over publishing the AppView's key as the account's `#atproto` key, which would stop its PDS signing commits for it. The moderator role check stays on Ozone's team (`listMembers`); no allowlist is needed.
- **Ozone UI hydration of `app.activitytracker.*` records.** It expects a Bluesky AppView. → Moderators copy the subject URI into the app's Moderation screen. Deeper Ozone integration is an open question.
- **Bluesky labeler stream volume.** → Drop non-athlete subjects in memory before touching the DB.
- **Extra joins on hot queries.** → Index `labels(uri, val)` and `actors(did, status)`. `EXPLAIN` the timeline with labels present.
- **Privacy expectations.** → The privacy policy and the report sheet must say moderators can view reported non-public content. The block confirmation stays as it is, since a block sends nothing to moderators. Access is gated on an open report and audit-logged.

## Migration Plan

1. Flyway `V28__labels_and_account_status.sql`: user-blocking took `V26__blocks_and_mutes.sql` and the segments work (`claude/segments-feature-exploration-4fd8d8`) `V27__segment_tracks.sql`. If another change takes V28 first, take the next free number.
2. Deploy Ozone and create the labeler identity before enabling the subscriber. With no labeler configured, the AppView runs without labels.
3. Release the clients after the AppView serves `labels`, because older clients ignore the field. Reporting needs the labeler DID in client config.
4. Rollback: disable the subscriber in config. Labels stop applying, and stored rows are ignored.

## Open Questions

- Whether to configure Ozone's external-link setting, so a subject in the Ozone queue links straight to the app's Moderation screen. This is a convenience only.
- Whether to also publish an `app.bsky.labeler.service` record for the labeler account so Bluesky clients can see it. Our app doesn't need it.
