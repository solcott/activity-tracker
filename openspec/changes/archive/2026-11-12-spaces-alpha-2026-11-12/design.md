# Design

## Context

- **The source.** Bluesky's guide `alpha-migrations/prompt-11-12-26.md` on the atproto `permissioned-data` branch, for the `0.0.0-spaces-alpha-20261112204144` release (atproto `b27a037`, which is `d4def6e9` plus release workflows). This change applies:
  - the renames: `authorizeSpaceSync`, `managedspace.checkAccess`, the `space` output field, the error names;
  - syncer registration through `authorizeSpaceSync`'s `notify`, with `confirmRegistration` and `notifySpaceUpdate`;
  - read MACs as RFC 9421 message signatures keyed by HPKE secret export;
  - `listRepoOps`' commit on every page, and `RepoNotFound` for a never-written repo;
  - the space declaration's `title`.

  Wire shapes come from the lexicons at `d4def6e9` (`lexicons/com/atproto/{space,managedspace,memberspace}`). The MAC and key-agreement algorithms come from `packages/space/src/mac.ts` and `kem.ts` at the same commit, and the vectors from the npm release.
- **Today's flow.**
  - `SpaceCredentials` exchanges a delegation token with `getSpaceCredential`, naming its key as `credentialKid`.
  - `SpaceSyncer` registers each space with `registerNotify` for 24 hours at `<serviceDid>#activitytracker_space_syncer`, renews it 2 hours early, and reconciles every 15 minutes.
  - The authority's PDS calls `notifyWrite` and `notifySpaceDeleted` with service auth addressed to that service id. `SpaceSyncer.deleted` drops a space on the deletion call alone.
  - `SpaceAccess` answers `checkUserAccess` per `user` and `access` with `{authorized}`.
  - `SpaceMac` sends `Atproto-Space-Want-Mac` and checks `Atproto-Space-Mac`, an HMAC keyed by ECDH with the author's `#atproto_kem` key, then HKDF.
- **Earlier releases.** The Nov 5 alpha (STAR framing, `RepoTakendown`/`RepoDeactivated`) was taken without a change of its own. Everything the Oct 29 change applied still holds.

## Goals / Non-Goals

**Goals:**
- Every space flow works against the 2026-11-12 image without changing who can read or write athlete spaces.
- The AppView keeps learning of writes promptly, and never drops a space's data on anything but the authority's own `SpaceDeleted`.
- A forged notification costs at most one sync.

**Non-Goals:**
- Member lists: `memberspace.putMembers` and the rest of the member-list methods. Our spaces use `managedspace` policies, and the smoke script only uses `putMembers` for its own memberspace.
- STAR `getRepo` and archive-based backfill. Backfill keeps `listRecords`, tied to the verified head by the set hash.
- The guide's labels guidance for spaces. Labels keep applying to what the AppView serves, as today.
- The guide's npm SDK section: client code has no atproto SDK by the hard rule, and the AppView's crypto stays a port.

## Decisions

1. **Registration rides on authorization.**
   - `SpaceCredentials` takes an optional `notify: suspend (SpaceUri) -> Notify?` and passes its result in every exchange. Only the AppView sets it, to `SyncRegistrations.notifyFor`; clients send no `notify`.
   - A registration lasts only as long as the credential of its latest authorization (10 minutes). So `registered_until` now holds that credential's expiry, and a sync within `renewalMargin` (6 minutes) of it reauthorizes first (`SpaceMember.authorize(space, renew = true)`), which renews the registration.
   - The reconcile interval drops from 15 to 5 minutes, so every space is pulled, and its registration renewed, well inside the credential's lifetime. With a 6-minute margin and a 5-minute reconcile, a healthy space never lets its registration lapse.
   - Alternative: reauthorize on a timer apart from syncs. Rejected: the sync loop already holds the per-space lock and backoff, and a reauthorization is one call.
2. **One stable token per space, in the database.**
   - `SyncRegistrations` makes the token on a space's first authorization (32 bytes from `SecureRandom`, base64url without padding) and stores it in `space_sync.notify_token` (V61, nullable). It's made only by an update conditioned on the column being null, so two first authorizations racing agree on whichever landed.
   - The token never changes after that, so every reauthorization renews the same registration rather than making a new one, and a notice in flight during a renewal still checks out.
   - `issued` compares in constant time (`MessageDigest.isEqual`).
   - Alternative: derive tokens with an HMAC of the space under a server secret. Rejected: it needs a new secret to configure and rotate, and the row exists anyway.
3. **No service auth on the host's callbacks.**
   - Upstream sends `confirmRegistration` and `notifySpaceUpdate` without service auth, so they're served through a new `unauthenticatedProcedure` that only decodes the input. The token is the authentication.
   - An unknown token, or one issued for another space, is `400 InvalidToken`. Upstream treats that answer as "unregister", so refusing also cleans up registrations the AppView no longer wants.
   - The `#activitytracker_space_syncer` service, `AppViewConfig.syncerService`, `SYNCER_FRAGMENT` and the syncer verifier are removed: nothing addresses tokens to them any more.
4. **A space is dropped only on `SpaceDeleted`.**
   - A `notifySpaceUpdate#spaceDeleted` is unauthenticated apart from its token, and a leaked token shouldn't be able to wipe a space. So it only clears `registered_until` and makes the space due, and the sync's own authorization decides: `SpaceDeleted` drops it, anything else keeps it.
   - `#registrationExpired` does the same, so the next sync reauthorizes and catches up.
   - `#repoWrite` and `#repoRemoved` for the authority's repo make the space due; for any other repo they're ignored, since only the authority writes. Unknown update kinds decode as `SpaceUpdate.Unknown` and are ignored.
   - `SpaceSyncer.deleted()` is removed.
   - `UpstreamFailure` (the host couldn't reach the managing app) and `RegistrationNotConfirmed` are ordinary failures that back off and keep the space, like `RepoTakendown`.
5. **`checkAccess` answers both questions.**
   - `SpaceAccess.check` computes `read` as before, keyed by `repo` instead of `user`, and `write` is always false.
   - A check with no `repo` comes from a request with no delegation token. No athlete space is anonymously readable, `public` included, so it reads nothing.
6. **Structured fields are ours.**
   - `sf` components cover the header as re-serialized from its parsed RFC 8941 dictionary, so the verifier needs a full parser and serializer. `StructuredFields` replaces the narrower `StructuredDictionary`.
   - klibs.io lists no Kotlin Multiplatform structured-fields library, so it's hand-written, with tests for canonical serialization, parsing and malformed input, and exercised by the npm vectors' headers.
7. **HPKE secret export is ours.**
   - Upstream's `encapsulate` is HPKE (RFC 9180) base mode with DHKEM(P-256, HKDF-SHA256) `0x0010`, HKDF-SHA256 `0x0001` and the export-only AEAD `0xFFFF`, `info` `atproto-mac`, and an empty exporter context.
   - klibs.io lists no multiplatform HPKE library, so `SpaceKem` spells out that one ciphersuite's labelled extract and expand over the HMAC-SHA256 already used, on top of the existing ECDH.
   - It's checked against the RFC 9180 export-only P-256 key pair, whose secret under our `info` was computed with Go's `crypto/hpke`, as upstream's `kem.test.ts` does, and against vectors from the npm release.
8. **The MAC verifier finds its signature by tag.**
   - It asks under the label `mac`, but accepts the one signature tagged with the profile under any label, requires its components to be exactly `("<profile>";sf)`, and checks `keyid` and `alg` only when present.
   - It rebuilds the signature base from the covered header, then checks that the header's fields match the body (a commit's `ver`, `repo`, `space`, `rev` and `data`; a record's `uri` and `cid`) and the request.
9. **We keep our credential signature label.** The credential signature on reads is unchanged. Upstream hosts find it by its `atproto-space-credential` tag, so the label stays `atproto-space-credential` and the existing vectors stand.
10. **Vectors from npm.** The release's `@atproto/space` and `@atproto/crypto` install from npm when every `@atproto/*` package is pinned to `0.0.0-spaces-alpha-20261112204144` with npm `overrides`. Plain caret ranges resolve a canary whose `workspace:*` spec breaks the install. `SpaceMacVectors` were regenerated that way. Running a script in the image (`docker run --entrypoint node`) still works as the alternative.

## Risks / Trade-offs

- [A registration lapses when the AppView is down for more than its credential's lifetime] → The host sends `#registrationExpired`, and whether it arrives or not the next sync reauthorizes and pulls from the last indexed revision, so nothing is lost but latency.
- [A leaked token lets anyone send notices for one space] → A notice only makes the space due, and a sync reads only what the authority's PDS serves under a MAC, so the worst case is extra syncs. Deletion still needs the authority's `SpaceDeleted`.
- [Reconciling every 5 minutes costs more calls] → One `authorizeSpaceSync` (only when renewing) and one `listRepoOps` page per space; athlete spaces are few per account.
- [Our structured-fields or HPKE port disagrees with upstream on an edge case] → The npm vectors and the RFC vectors pin both. A mismatch shows as a MAC failure that backs off and indexes nothing, never as data loss.
- [A deleted space lingers until its next sync] → The deletion notice makes it due at once, so that's seconds, not the reconcile interval.

## Migration Plan

1. Re-pin `pds-spaces-alpha` to the 2026-11-12 digest and run the CSRF patch check in the Dockerfile build.
2. Wipe and reseed the local stack (`scripts/dev.sh down` with volumes, then `scripts/dev.sh --seed alice`); registrations and credentials from the old image don't carry over.
3. Flyway `V61__space_sync_notify_token.sql` adds the nullable column. Each space gets its token on its next authorization.
4. Rollback means reverting the commit and the image pin together. The AppView ignores `notify_token` once it's unused.
