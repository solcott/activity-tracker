# Tasks

## 1. Lexicon: the `privateDetails` record and `updateSpace`

- [x] 1.1 Add `lexicons/app/activitytracker/actor/privateDetails.json` (D1: record, `key: literal:self`, required `details` ref `actor.defs#details`, description saying it lives only in a tier space). Add `Nsid.ACTOR_PRIVATE_DETAILS`, kept out of `indexedCollections`. Verify the NSID matches the collection already listed in `space/athlete.json`.
- [x] 1.2 Add `PrivateDetailsRecord` (`@Serializable`, `$type`, `RKEY = "self"`) to `core/lexicon` and a round-trip case in the lexicon serialization tests. Verify `./gradlew :core-lexicon:jvmTest`.
- [x] 1.3 Mirror `com.atproto.simplespace.updateSpace` in `atproto/Space.kt` as `SimpleSpace.UpdateSpace`, with `Input(space, readPolicy?, writePolicy?, appAccess?)`, no output, and the error names copied from the pinned image's `updateSpace.json`. Add an encoding case to `SpaceSerializationTest`. Verify `./gradlew :core-lexicon:jvmTest`.
- [x] 1.4 Run the `lexicon-auditor` agent on `actor.privateDetails` and `simplespace.updateSpace`, and fix every mismatch it reports.

## 2. `core/atproto`: `updateSpace` and the managing-app lookup

- [x] 2.1 Add `SpaceClient.updateSpace(space, readPolicy?, writePolicy?, appAccess?)` (session-authenticated, on the athlete's PDS). Cover it in `SpaceClientTest` with `MockEngine`: request body, and `SpaceNotFound` surfacing as an `XrpcException`. Verify `./gradlew :core-atproto:jvmTest --tests "*.SpaceClientTest"`.
- [x] 2.2 Add `ManagingApp.resolve(http, appViewUrl)` (D4). It fetches `/.well-known/did.json` and returns `id#activitytracker_space`, and throws when no `AtprotoSpaceService` with that id is listed. Test it with `MockEngine`: a tunnel `id` fetched from a localhost URL, a missing service and a wrong type. Verify `./gradlew :core-atproto:jvmTest`.

## 3. `core/data`: saving and reading details in spaces

- [x] 3.1 Move `DetailsVisibility` from `core/lexicon` to `core/data/.../profile` (D2), keeping its name and fields, and update imports in `core/data`, `app/presenters`, `composeApp` and the tests. Verify `./gradlew :core-data:jvmTest :app-presenters:jvmTest` compiles and passes.
- [x] 3.2 Add `AthleteSpaces` (`@SingleIn(SessionScope)`, D3). `ensure(tier)` creates the tier space with `ManagingApp(managingApp)`/`MemberList()`/`Open()`. On `AlreadyExists` it runs `getSpace`, plus `updateSpace` if the read policy differs. It memoizes per tier and shares one in-flight ensure per tier. Expose the tier `SpaceUri`s. Test with `MockEngine`: create, already exists and current, already exists and stale (one `updateSpace`), two concurrent `ensure`s making one `createSpace`. Verify `./gradlew :core-data:jvmTest`.
- [x] 3.3 Rewrite `ProfileRepository.ownProfile()`/`ownDetails` to read the `mutuals` and `self` tier records with `spaces.getRecord`, where `NotFound` means an empty tier, instead of `getPrivateDetails`. Catch `ScopeMissingError` on the tier reads only, and set the new `OwnProfile.needsSignIn` (D9). Test in `ProfileRepositoryTest`: details from all three places with the right visibilities, no spaces yet, and a scope-missing session loading public details with `needsSignIn`. Verify `./gradlew :core-data:jvmTest --tests "*.ProfileRepositoryTest"`.
- [x] 3.4 Rewrite `ProfileRepository.save` to the add → record → trim order (D5), with `AthleteSpaces.ensure` only for tiers that will hold a detail, and `deleteRecord` for tiers that end up empty. Remove `putPrivateDetails`. Test with `MockEngine` call recording:
  - each visibility lands in exactly one place;
  - Only me → Mutuals and Mutuals → Public each write the new home before trimming the old;
  - a failure after step 1 leaves the detail stored;
  - a public-only save creates no space;
  - a tier that is emptied gets deleted.

  Verify `./gradlew :core-data:jvmTest --tests "*.ProfileRepositoryTest"`.
- [x] 3.5 Make `ProfileRepository.profile(actor)` merge tier details (D7). For your own profile, read both tiers with `read_self`. For a followed athlete, read their `mutuals` tier. For anyone else, make no space call. `NotFound`/`NoAccess`/a thrown failure all show public details only (logged with Kermit, without contents), and the public value wins over a tier value. Keep `pendingOwnProfile`. Test: a mutual sees the Mutuals detail, a refused follower sees public only, a non-follower makes no space request, a failed read still returns the profile, and the public value wins. Verify `./gradlew :core-data:jvmTest`.
- [x] 3.6 Update `.agents/rules/data-and-auth.md`: where non-public details live, tier spaces created on save through `AthleteSpaces`, the managing-app id from the AppView's DID document, and profile saves staying direct rather than outbox (D6). Verify `scripts/check-agent-sync.sh` passes.

## 4. Edit profile: sign in again

- [x] 4.1 Add `UiError.Kind.SignInAgain` and a sign-in-again event to `EditProfileScreen`'s contract. In `EditProfilePresenter`, map a `ScopeMissingError` save failure and `OwnProfile.needsSignIn` to it, and have the event call `AuthRepository.signOut()`. Test in `EditProfilePresenterTest`: a scope-missing save shows `SignInAgain`, loading with `needsSignIn` shows it and still lets public fields be edited, and the event signs out. Verify `./gradlew :app-presenters:jvmTest --tests "*.EditProfilePresenterTest"`.
- [x] 4.2 Show the `SignInAgain` error in the edit-profile UI with localized text and a "Sign in again" button (an `ui/adaptive` wrapper). Verify `./gradlew :composeApp:jvmTest composeStabilityCheck` and a screenshot of the state on the desktop app via the hot-reload MCP. Done 2026-10-13: the screenshot was taken on the web app during 7.3 (the desktop app wasn't running); same shared UI.

## 5. AppView: public details only

- [x] 5.1 Remove `PrivateDetails.put/get`, the `getPrivateDetails`/`putPrivateDetails` routes and their wiring in `Application.kt`. Replace `visibleDetails` with `publicDetails(actor)`, and move `isMutual`/`follows` beside it (D10). Delete `PrivateDetails.kt`. Rewrite `ProfileDetailsTest`: `getProfile` returns only record details for self, a mutual and a stranger, and `putPrivateDetails` is not implemented. Verify `./gradlew :server-appview:test --tests "*.ProfileDetailsTest" --tests "*.SpaceAccessTest"`.
- [x] 5.2 Change the zone rule in `ActivityStreamsViews` to `isSelf || publicDetails(actor)?.training != null`. Update `ActivityStreamsTest`: a mutual gets no zones when training isn't public, any permitted viewer gets them when it is, and the athlete always does. Verify `./gradlew :server-appview:test --tests "*.ActivityStreamsTest"`.
- [x] 5.3 Drop `ActorPrivateDetails` from `Tables.kt` and from `Indexer.deleteActor`, and add `V13__drop_private_details.sql` (`DROP TABLE actor_private_details;` with a comment). Verify `./gradlew :server-appview:test` passes on H2 with the full migration chain.
- [x] 5.4 Update `.agents/rules/appview.md`: the AppView stores nothing private, the zone rule is public training, and `isMutual` serves `checkUserAccess` and stream visibility. Verify `scripts/check-agent-sync.sh` passes.

## 6. Lexicon removals and docs

- [x] 6.1 Delete `putPrivateDetails.json`, `getPrivateDetails.json` and `defs#detailsVisibility`. Remove `PutPrivateDetails`/`GetPrivateDetails` from `core/lexicon` and the two NSIDs from `Nsid`. Update `getProfile`'s and `defs#profileView`'s `details` descriptions to say public details only. Run the `lexicon-auditor` agent on the removed NSIDs and fix every mismatch it reports. Verify `./gradlew check`.
- [x] 6.2 Update AGENTS.md: the architecture paragraph about "the one thing it stores", and the "never put private data in a record" line now pointing at tier spaces. Update `.agents/rules/lexicons.md` where it mentions the private store. Verify `scripts/check-agent-sync.sh` passes.
- [x] 6.3 Update BACKLOG.md:
  - Replace "Move private profile details to an atproto Space" with what remains: the production namespace and a negative cache for one-way followers, if needed.
  - In "Move activity streams to a Space", note that zones should come back for mutuals.
  - Add "AppView as a consented member for aggregation" (D8): an AppView signing key, a `did:web` delegation spike, opt-in, and `checkUserAccess` admitting the AppView.

  Verify by reading the diff.

## 7. End-to-end on the local stack

- [x] 7.1 With `scripts/dev.sh --seed alice` (tunnel on) plus bob (mutual of alice) and carol (follows alice only):
  - alice saves her weight as Mutuals and her birthdate as Only me, and sees both on edit profile and on her own profile;
  - bob sees the weight and not the birthdate;
  - carol sees neither, with no error;
  - `.dev/appview.log` shows `checkUserAccess` for bob and carol.

  Record the result in this change's design.md under Migration Plan step 3. Done 2026-10-13 on the web app; see design.md.
- [x] 7.2 Restart the tunnel (a new AppView DID), have bob reload alice's profile (refused), then have alice re-save. Verify that bob sees the weight again, which shows the stale managing app was replaced.
- [x] 7.3 Sign in on a session from before this change (or remove `space:` from the scope temporarily). Verify that edit profile shows the sign-in-again message, and that the action returns to the login screen.

## Workflow follow-up

- Archive the change with `/opsx:archive` once the end-to-end checks pass, which syncs `profile/private-details` and the `spaces/client-protocol` delta into `openspec/specs/`.
