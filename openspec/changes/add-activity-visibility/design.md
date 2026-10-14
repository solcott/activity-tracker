# Design

## Context

See proposal.md for the motivation. These are the current constraints that shape the approach:

- **Records.** Activities are public `feed.activity` records written through the Room outbox (`OutboxWriter` → `OutboxProcessor` → `putRecord`). The AppView indexes them from Jetstream into `activities` (keyed by `uri`) and serves them from `Views` (`activityPage`, `hydrate`, `shownInFeeds`, `profile.activitiesCount`).
- **Streams.** `feed.activityStreams` already carries a `visibility` field. Its records are public, so only `ActivityStreamsViews` enforces it (`TODO(spaces)`). `space.athlete` already lists `feed.activityStreams` as one of its collections.
- **Tier spaces.** Each athlete has `app.activitytracker.space.athlete` tier spaces (`followers`, `mutuals`, `self`), created lazily by `AthleteSpaces.ensure` with the AppView as managing app. `SpaceAccess.canRead` decides reads. Profile saves are direct calls, not outbox writes, because a privacy change that silently retried later would be the wrong failure.
- **What the pinned Spaces PDS offers** (read from the `pds-spaces-alpha` image):
  - `getDelegationToken` / `getSpaceCredential`, with the delegation issuer's key resolved from any DID document's `#atproto` or `#atproto_space` method.
  - `registerNotify(space, service)`, with a 24 h registration.
  - Outbound `notifyWrite{space, repo, repoRev, hash, spaceRev}`, sent with service auth (`iss` = the authority, `aud` = the registered service id, `lxm` = `com.atproto.space.notifyWrite`). The 24 h retry covers only the hop from the writer's PDS to the authority's; the authority's fan-out to registered services runs once from a background queue, and a failed delivery is only logged (see Spike outcome).
  - `listRepoOps(space, repo, since)`, which returns ops and a signed commit.
  - `listRepos` and space `getBlob`.
- **AppView dependencies.** The AppView already depends on `:core-atproto`, which has `SpaceClient`, `SpaceCredentials` and `HttpMessageSignature` (currently `internal`).

## Goals / Non-Goals

**Goals:**
- One AppView read path for every tier: views filter rows by tier and viewer, and clients keep calling the same queries.
- No public trace of a non-public activity: nothing in the repo, nothing on Jetstream.
- Prove the Spaces syncer path in a spike before building on it.

**Non-Goals:**
- Caching viewer-specific results. Tier filtering runs in SQL on each query.
- Supporting a PDS without the Spaces alpha. Those athletes keep Public-only activities: the picker is disabled when their session lacks the `space:` scope.

## Decisions

### 1. Keep one `activities` table, keyed by the logical URI, with a `visibility` column
A space-held activity is indexed under `at://{did}/app.activitytracker.feed.activity/{rkey}`, the same URI it would have in the repo. A new `visibility` column holds the tier (`public` for repo records). A new `origin` column holds `repo` or the tier space URI it came from, so deletes from one origin can't remove a row another origin owns. (`source` would clash with Exposed's `Table.source`.)
- *Why:* `getActivity`, the detail navigation, `recordingOf`, streams' `activity` field and future in-space kudos all address activities by URI, so a move between tiers must not change it.
- *Mid-move duplicates:* the primary key stays `uri`. When two origins hold the same rkey, the narrower tier wins the row and the wider copy waits in `record_shadows` (keyed by uri and origin, holding the raw record). When the row's origin deletes it, the shadow is promoted by indexing it again; when the shadow's origin deletes it, only the shadow goes. Without the shadow, a Self → Followers move (written wider first) would leave nothing indexed once the `self` copy was deleted. The same holds for `activity_streams`.
- *Alternative rejected:* a separate `private_activities` table. It would double every query and every hydrate path.

### 2. Filter by tier with one SQL predicate
`visibleTo(viewer)` = `visibility = 'public'`, OR `author = viewer`, OR (`visibility = 'followers'` AND an effective follow by viewer of author), OR (`visibility = 'mutuals'` AND effective follows both ways).
- It's built from the existing `effectiveFollow()`, and is AND-ed into `activityPage`, `authorActivitiesByStart`, `activity`, `recordingsOf`, `shownInFeeds` and `activitiesCount`.
- An unknown `visibility` value is treated as `self`.
- A `recordingOf` link is honoured only when both rows share a visibility.
- Kudos and comment counts, `viewer.kudos` and `getKudos` are returned only for `public` rows. Kudos rows are kept, so they reappear if the activity goes Public again.

### 3. The AppView mints its own delegation tokens
- **Key.** A new `APPVIEW_SIGNING_KEY` (a P-256 private scalar as 64 hex chars, like the PDS's `*_PRIVATE_KEY_HEX`; generated into `docker/.env` by `scripts/dev.sh`, which the AppView's Gradle `run` task reads) is published in `did.json` as `#atproto` (`Multikey`), next to an `#activitytracker_space_syncer` service (type `AtprotoSpaceSyncer`).
- **Tokens.** The AppView signs `atproto-space-delegation+jwt` tokens with `iss` = service DID, `aud` = `{authority}#atproto_space_host`, `sub` = the space, a 60 s lifetime and a random `jti`. It binds credentials to a P-256 `did:key` held in memory.
- **Shared code.** `HttpMessageSignature` and the exchange step are refactored in `:core-atproto` so that `SpaceCredentials` takes a "delegation token source": the viewer's PDS for clients, a local signer for the AppView. That keeps one signature implementation, checked against the reference vectors.
- *Alternative rejected:* giving the AppView a PDS account. That would put a second identity and its credentials into ops for no gain.

### 4. Discovery through `notifySpace`, then `registerNotify` plus reconcile
- **Discovery.** Clients call `app.activitytracker.space.notifySpace` (service auth, `issuer` = authority) after `AthleteSpaces.ensure` succeeds, and again on every save that touches a tier. The call is cheap and idempotent.
- **Sync state.** The AppView records the space in a new `space_sync` table (space, authority, the authority repo's last indexed rev, registration expiry, failure count, next attempt). Only the authority writes to an athlete's space, so one repo per space is synced. It then joins, registers, and backfills. The PDS may compact the op log, so `listRepoOps` without `since` isn't a full history: the backfill reads the head revision (`listRepoOps` with `excludeValues` until `commit`), lists the current `feed.activity` and `feed.activityStreams` records with `listRecords`, and then follows ops after that revision. Replaying ops after it is harmless because applying them is idempotent and in order.
- **Notifications.** A `notifyWrite` route verifies service auth with a dedicated audience (`<serviceDid>#activitytracker_space_syncer`). Keeping this audience separate means a managing-app or AppView token can't be replayed here. The route then schedules a pull for that space; pulls for one space never run concurrently.
- **Reconcile.** A background job renews registrations within 2 h of expiry and pulls every space every 15 minutes. It backs off per space on failure. On `SpaceDeleted` it drops the space and its rows.
- **Collections.** Ops are applied through the same `upsertActivity`/`delete`/streams code the Jetstream indexer uses, with `visibility` and `origin` set from the space key. Ops for any other collection are dropped before they touch the database.
- *Alternative rejected:* clients ping the AppView after every write. The PDS's notifications don't depend on the client staying online. They aren't retried towards the AppView, so the reconcile is what makes delivery reliable; a client ping wouldn't add to that.

### 5. Client writes target a place, not just a collection
- **Outbox.** `OutboxEntity` gains a nullable `space` column (Room auto-migration, `version` 1 → 2). `OutboxProcessor` sends a row with a space to space `putRecord`/`deleteRecord`, ensuring the space exists first.
- **New non-public activities use the outbox.** A delay never makes data wider than chosen, so recording offline keeps working.
- **Visibility changes are direct calls**, as profile saves are. The sequence is: write the new place, then delete the old place, then move the charts and recordings. Failures surface in the UI, and a retry is idempotent because `putRecord` and delete are.
- **Blobs move with their record.** Checked on the local PDS: a repo-uploaded blob referenced by both a repo record and a space record survives deleting either one, and once only the space record holds it, public `sync.getBlob` refuses it (and the reverse for a space → repo move). A move writes the new place with the same blob refs, then deletes the old; nothing is re-uploaded.
- **Charts move with the activity.** `ActivityStreamsStore` gains a space-backed binding. `setVisibility` becomes a move between places, and the `visibility` field stays in the record so legacy repo records keep working.
- **Blobs.** Photos, the track and stream data are uploaded with the ordinary `com.atproto.repo.uploadBlob` and referenced from the space record; the spike proved this path. The space `putRecord` makes the blob permanent and ties it to the space record, after which it's served only through space `getBlob` (public `sync.getBlob` answers `BlobNotFound`), and deleting the record frees it. No new upload call is needed.

### 6. Defaults live in `actor.activityDefaults` (rkey `self`) in the `self` space
The record has `{visibility, chartsVisibility}`. The client reads it with the owner session at session start and when Settings opens, caches it in memory, and falls back to Public on any failure. Writes are direct calls from Settings. The AppView ignores the collection.

### 7. Stream data and media for space activities go through the AppView
`BlobRedirects` and `StreamSource` currently redirect to or fetch from the athlete's PDS. For rows whose `origin` is a space, the AppView:
1. checks `visibleTo(viewer)`;
2. fetches the blob with `getBlob` using its credential;
3. streams it back with `Cache-Control: private`.

Stream data stays cached by CID in `activity_stream_data`, which now also holds non-public data. The AppView's at-rest posture covers that.

Image loaders fetch photo URLs without service auth, so the view can't rely on the request to say who's asking. A non-public activity's photo URL is signed for the viewer the view was built for (`?viewer=&exp=&sig=`, an HMAC keyed from `APPVIEW_SIGNING_KEY`, expiring at the next hour boundary after one hour so it stays stable and cacheable on the device). The media route checks the signature, then re-checks `visibleTo` for that viewer, so an unfollow takes effect at once even for a URL already handed out. Public photos keep their plain, publicly cacheable redirect. The `track` blob isn't served by the AppView at all (clients draw maps from the polyline), so nothing changes for it. Streams are also hidden from a viewer who may not see their activity, when it's indexed, which enforces the charts cap server-side too.

### 8. Interactions are keyed by the logical activity URI
`kudos.subject_uri` and `comments.subject_uri` already store the repo-style URI. Because of decision 1, they keep matching after a move. The follow-up's in-space kudos will index into the same tables, so no migration is needed.

## Spike outcome: proceed

Run on 2026-10-14 against `scripts/dev.sh` (pinned `pds-spaces-alpha`) with `docker/scripts/syncer-smoke.sh alice --blob`. The stub is a `did:web` on its own quick tunnel, publishing a fresh P-256 `#atproto` Multikey and an `#activitytracker_space_syncer` service. To keep the spike independent of the AppView's access rules, alice's space is a member-list smoke space (`app.activitytracker.smoke/syncer`) with the stub added as a reader, rather than her managing-app `followers` space; admitting the AppView through `checkUserAccess` is task 3.3, checked end to end in 10.1. Results:

- **Delegation from a `did:web` issuer works.** `getSpaceCredential` accepted the stub-signed `atproto-space-delegation+jwt` (ES256, `kid #atproto`, `aud {authority}#atproto_space_host`) and returned a 600 s credential with `iss` = the authority and `cnf.kid` = the binding `did:key`. Replaying the token answered `JwtReplayed`.
- **Notifications arrive.** `registerNotify` with the credential returned an `expiresAt` 24 h out. A `putRecord` by alice reached the stub as `notifyWrite {space, repo, repoRev, hash: {$bytes}, spaceRev}` with service auth `iss` = alice, `aud` = `<stub DID>#activitytracker_space_syncer` and `lxm` = `com.atproto.space.notifyWrite`. A `deleteRecord` produced a second notification.
- **Ops read back.** `listRepoOps` with the credential returned the put (`rev`, `cid`, `value`), and with `since` returned only later ops. A delete op has `cid: null` and the old `prev`. Without credentials the call answers `AuthMissing`.
- **Blobs** (task 1.2). A blob from repo `uploadBlob`, referenced by a space record, round-tripped byte for byte through space `getBlob` with the stub's credential, with its `Content-Type`. Public `sync.getBlob` and an uncredentialed space `getBlob` refused it, and after the record was deleted space `getBlob` answered `BlobNotFound`.
- **Correction from reading the PDS source.** The authority's fan-out to registered services isn't retried (`processNotifyWrite` queues one attempt per recipient and logs failures). The reconcile in decision 4 is therefore the delivery guarantee, not a backstop.

Nothing pointed back towards option A, so the design proceeds as written.

**Follow-up check with the real AppView** (after groups 3–6): alice's managing-app `followers` space, announced with `notifySpace`, was joined (`checkUserAccess` admitted the AppView's DID), backfilled, and kept up by `notifyWrite` through the tunnel within about a second for a put and a delete. The first attempt failed with `missing or bad key (#atproto) in did doc`: the PDS still held the AppView's DID document from before the key existed. It caches DID documents and doesn't refresh on a missing key, so the per-space backoff (first retry after 1 minute) is what recovers. The same applies after a key rotation.

## Risks / Trade-offs

- **[The PDS rejects a `did:web` delegation issuer, or `registerNotify` misbehaves.]** → The spike (task group 1) runs first, against the local stack through the tunnel. If it fails, stop and revisit the design (client fan-out, option A) before writing more code.
- **[Space records can't reference blobs, or space blob upload differs from the repo's.]** → Retired by the spike: repo-uploaded blobs work in space records and stay private.
- **[The AppView now holds non-public and Only-me data.]**
  - A database leak exposes it. Mitigation: index only activity and stream collections, never `privateDetails`.
  - `AGENTS.md` and `appview.md` must state the new rule plainly.
  - Treat backups as sensitive before any shared deployment (a `BACKLOG.md` "Before deploying" entry).
- **[Narrowing a Public activity can't recall firehose copies.]** → The client warns before saving (spec). It's documented as a limitation.
- **[Mid-move duplicates, or a stale tier after a missed notification.]** → The narrower tier wins (decision 1), and a reconcile every 15 minutes bounds staleness. Widening, such as Followers → Public, shows up via Jetstream first, which is safe.
- **[Spaces alpha churn.]** → Spaces wire types stay in `Space.kt`, and the syncer is isolated behind one AppView service. Bumping the PDS image re-runs the spike's smoke script.
- **[Tier filtering makes timeline queries slower.]** → Add an index on `(author_did, visibility, sort_at)`. The predicate reuses the follow indexes. Checked with `EXPLAIN ANALYZE` on the local Postgres (task 7.4) with 200k synthetic activities, a quarter per tier, 2,000 athletes and a viewer following 200 of them (100 mutually), seeded in a rolled-back transaction:
  - *Timeline:* Postgres evaluates each `EXISTS` follow check once as a hashed subplan (the viewer's follows, its followers), then walks `activities_sort_idx` backwards, filtering per row by hash lookup. 50 rows in about 12 ms (execution; plus JIT overhead on this small machine). The new index isn't used here, and isn't needed.
  - *An athlete's activities and the profile count:* a `BitmapOr` over `activities_author_visibility_sort_idx`, one bitmap per tier branch, in about 1 ms.

## Migration Plan

1. **Flyway.** `V15__activity_visibility.sql` adds `activities.visibility` (default `'public'`) and `activities.origin` (default `'repo'`), `activity_streams.origin`, the new index, and the `record_shadows` and `space_sync` tables. It's H2-portable.
2. **Room.** Auto-migration 1 → 2 for `OutboxEntity.space`.
3. **Local stack.** `scripts/dev.sh` generates `APPVIEW_SIGNING_KEY` once. Existing local spaces need a fresh `notifySpace`; re-saving the profile or publishing does that.
4. **Rollback.** With `APPVIEW_SIGNING_KEY` unset, the AppView stops syncing (spec), and non-public activities disappear from feeds but stay safe on PDSes. The schema columns are additive.

## Open Questions

- **Reconcile and renewal cadence.** The 15 minutes and 2 h margin are starting values. Tune them after observing the local stack. This doesn't affect the specs.
- **Whether to batch `notifySpace` per session.** It's an optimization only.
