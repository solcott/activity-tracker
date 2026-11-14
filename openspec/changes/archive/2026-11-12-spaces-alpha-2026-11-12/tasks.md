# Tasks

## 1. Image and reference material

- [x] 1.1 Resolve the `ghcr.io/bluesky-social/atproto:pds-spaces-alpha` digest built on 2026-11-12 (`docker buildx imagetools inspect`), and pin it in `docker/pds.Dockerfile` with the date, commit and release in the comment. Verify `docker compose -f docker/compose.yaml build pds` succeeds with the CSRF patch's checks intact.
- [x] 1.2 Copy the `com.atproto.space.{authorizeSpaceSync,confirmRegistration,notifySpaceUpdate,listRepoOps,defs}` and `com.atproto.managedspace.checkAccess` lexicons and `packages/space/src/{mac,kem}.ts`, at `d4def6e9`, into the session scratchpad. Verify each file is there and the lexicons match the new image's `/app/lexicons`.

## 2. Wire types (`core/lexicon`)

- [x] 2.1 Update the Spaces mirror in `atproto/Space.kt` (design D1, D4, D5):
  - `Space.AuthorizeSpaceSync` (`credentialKey`, optional `Notify`, output `spaceRev`), replacing `GetSpaceCredential`;
  - `Space.ConfirmRegistration`, `Space.NotifySpaceUpdate` and the `SpaceUpdate` open union (`#repoWrite`, `#repoRemoved`, `#spaceDeleted`, `#registrationExpired`, unknown), replacing `RegisterNotify`, `NotifyWrite` and `NotifySpaceDeleted`;
  - `ManagedSpace.CheckAccess` (`repo`, `clientId`, output `{read, write}`), replacing `CheckUserAccess`;
  - `CreateSpace`/`GetSpace` output `space`; `ListRepoOps` with `commit` on every page; `ListRecords` without `reverse`;
  - `SpaceErrors`: `ACCOUNT_NOT_AUTHORIZED`, `INVALID_SPACE_MAC`, new `REGISTRATION_NOT_CONFIRMED` and `UPSTREAM_FAILURE`, `NOT_SPACE_OWNER` removed.

  Verify `SpaceSerializationTest` round-trips each `SpaceUpdate` member, keeps an unknown `$type` as `Unknown`, and that `./gradlew :core-lexicon:jvmTest` passes.
- [x] 2.2 Rename the space declaration's `name` to `title` in `lexicons/app/activitytracker/space/athlete.json`.

## 3. Client (`core/atproto`, `core/data`)

- [x] 3.1 Add `space/StructuredFields.kt`, a full RFC 8941 dictionary parser and serializer, and delete `StructuredDictionary.kt` (design D6). Verify `StructuredFieldsTest` covers canonical serialization, inner lists with parameters, a duplicate key and malformed input.
- [x] 3.2 Rewrite `SpaceKem` as HPKE secret export (design D7). Verify `SpaceMacTest` derives the RFC 9180 export-only P-256 key pair's secret under `info` `atproto-mac` as Go's `crypto/hpke` does.
- [x] 3.3 Rewrite `SpaceMac` as RFC 9421 `hmac-sha256` signatures with `Accept-Signature`, `Atproto-Space-Commit` and `Atproto-Space-Record` (design D8), update `HttpMessageSignature` and `SpaceMacHost`, and keep the credential signature's label (design D9). Regenerate `SpaceMacVectors` from the npm release (design D10). Verify `SpaceMacTest` covers the vectors, a signature under another label, extra components, a wrong `keyid` or `alg`, and a header that disagrees with the body or the request.
- [x] 3.4 Change `SpaceCredentials` to `authorizeSpaceSync` with an optional `notify` callback and `Credential.spaceRev`, `SpaceMember` to `Accept-Signature` and `authorize(space, renew)`, and `SpaceClient` to the `space` output. Update `SpaceClientTest`, `SpaceCredentialsTest`, `SpaceFixtures`, `FakeAthletePds` and the core-data and presenter fixtures that stub the renamed methods. Verify `./gradlew :core-atproto:jvmTest :core-data:jvmTest` and the presenter tests pass.
- [x] 3.5 Update `.agents/rules/data-and-auth.md` where it names the exchange, the MAC headers, the key agreement or the denial errors.

## 4. Managing app (AppView)

- [x] 4.1 Route `ManagedSpace.CheckAccess` in `api/Routes.kt` and answer `{read, write}` from `SpaceAccess` keyed by `repo`, with no `repo` reading nothing and `write` always false (design D5). Update `SpaceAccessTest` and `FakeSpacesPds`. Verify tests cover each scenario of "Access check endpoint" and "Callouts authenticated as the space's authority", including the old `lxm` refused with 401 and "Old method name".

## 5. AppView syncer

- [x] 5.1 Add Flyway `V61__space_sync_notify_token.sql` (`notify_token VARCHAR(64) NULL`) and the `SpaceSync` column in `db/Tables.kt`, H2-portable per `.agents/rules/appview.md`. Verify `./gradlew :server-appview:test` migrates H2 cleanly.
- [x] 5.2 Add `sync/SyncRegistrations.kt` (design D2) and wire it into `SpaceCredentials`' `notify` in `Application.kt`. Verify `SpaceSyncTest` covers the same token carried by every reauthorization and `confirmRegistration` answered only for the token issued for its space.
- [x] 5.3 Serve `confirmRegistration` and `notifySpaceUpdate` through `unauthenticatedProcedure`, checked by token, and remove `notifyWrite`, `notifySpaceDeleted`, the syncer DID-document entry, `AppViewConfig.syncerService` and `SYNCER_FRAGMENT` (design D3). Verify tests cover each scenario of "Writes indexed on notification" and "Registered for update notifications", including `400 InvalidToken` for a missing, wrong or other space's token, and that `AppViewTest` finds no syncer entry in `did.json`.
- [x] 5.4 Change `SpaceSyncer`: reconcile every 5 minutes, reauthorize within 6 minutes of `registered_until` through `SpaceMember.authorize`, `notified(space, reauthorize)` for deletion and expiry notices, `deleted()` removed, and `listRepoOps` reading the commit from every page (design D1, D4). Verify `SpaceSyncTest` covers each scenario of "Deletion notices confirmed before a space is dropped" and "Space sync failures are contained", including `UpstreamFailure` and `RegistrationNotConfirmed` backing off and a deletion notice for a live space removing nothing. Verify `./gradlew :server-appview:test` passes.
- [x] 5.5 Update `.agents/rules/appview.md` (the managing-app and syncer paragraphs and the routes note) and the AGENTS.md data-flow paragraph (`checkAccess`). Verify `scripts/check-agent-sync.sh` passes.

## 6. Local stack and smoke scripts

- [x] 6.1 Update `docker/scripts/syncer-smoke.py` (`putMembers`, `authorizeSpaceSync` with `notify`, a stub serving `confirmRegistration` and `notifySpaceUpdate` with a token check, HPKE, `Accept-Signature`), `syncer-smoke.sh`, `spaces-smoke.sh` and the `scripts/dev.sh` wording. Verify each runs green on a fresh stack (`scripts/dev.sh down` with volumes, then `scripts/dev.sh --seed alice`).
- [x] 6.2 Update `.agents/rules/docker.md` (image date and protocol, the tunnel note, and the "Bumping" note on regenerating vectors from npm) and the `local-stack` skill's troubleshooting rows (`UpstreamFailure`, `RegistrationNotConfirmed`, `InvalidSpaceMac`). Verify `scripts/check-agent-sync.sh` passes.

## 7. Integration checks

- [x] 7.1 `./gradlew check` passes.
- [x] 7.2 Live registration: with the AppView running on the fresh stack, record a Followers activity as alice. Confirm the AppView's `authorizeSpaceSync` was confirmed, a `notifySpaceUpdate` arrived and the AppView syncs and serves the activity, with no MAC failure in its log.
- [x] 7.3 Live deletion: delete alice's `followers` space. Confirm the AppView, prompted by the notice, is answered `SpaceDeleted` and stops serving the activity.
- [x] 7.4 Live managing app: confirm a mutual reads alice's `mutuals` space through her PDS, and a non-mutual is refused.

## Workflow follow-up

- Archive the change with `/opsx:archive` once the tasks are done and the specs are synced.
