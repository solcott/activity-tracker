# Tasks

## 1. Display-only names become optional

- [x] 1.1 Change `name` to `String? = null` on `SegmentLeaderboardScreen` and `SegmentMayorsScreen`. When it's null, the presenter shows the segment's current name from `getSegment`; in-app callers still pass it.
  - Verify with `SegmentLeaderboardPresenterTest` and `SegmentMayorsPresenterTest` cases for a null name, where the title arrives after the segment loads.
- [x] 1.2 Do the same for `DiveSiteRegularsScreen` (the name comes from `getDiveSite`) and `ImportBatchScreen` (from the batch athlete's profile).
  - Verify with presenter tests for each screen with a null name.
- [x] 1.3 Update the samples in `:app-fixtures`, the Compose UIs and the native views (`SegmentLeaderboardView`, `SegmentMayorsView`, `DiveSiteRegularsView`, `ImportBatchView`) so they read the name as optional.
  - Verify the iOS build: `xcodebuild … build`.
  - Verify the changed screens' UI tests and `:androidApp` screenshot tests still pass, with references unchanged.

## 2. Link grammar (`:app-links`)

- [x] 2.1 Create `app/links` as `:app-links` (KMP library, no Compose or Metro), depending on `:core-lexicon`, `:app-common-api` and every feature `-api`.
  - Add a `ktor-http` entry to `gradle/libs.versions.toml` and register the module in `settings.gradle.kts`.
  - Extend `checkModuleBoundaries` so that `:app-links` depending on a presenter or UI module fails.
  - Verify with a `checkModuleBoundaries` test case and `./gradlew checkModuleBoundaries`.
- [x] 2.2 Implement:
  - `AppLinks(host).parse(url)`, using Ktor's `Url`, `URLBuilder` and `encodeURLPathPart`. It accepts https on the host, `activitytracker://` and bare paths, and refuses `/oauth/`, `/xrpc/` and `/.well-known/`.
  - `LinkRoute`, the routes under `routes/` per feature (the grammar in design.md, including main's `PathScreen` as `/trail`), `AppLinkRoutes.all`, `excluded` and `topLevelPrefixes`, and `pathOf(screen)`.
  - Verify with `LinkRoundTripTest`, run as `./gradlew :app-links:jvmTest`. It covers:
    - every screen sample round-trips to its canonical form;
    - `did:web:localhost%3A8081` survives;
    - `?board=`, `?sport=` and `?combineInto=` are kept;
    - refused prefixes and other hosts are rejected;
    - the scheme and https forms are equal.
- [x] 2.3 Add `LinkCoverageTest`, which scans `feature/*/api/src/commonMain` for `@CircuitSerializable` screens (same regex as `IosScreenRegistry`). It fails for a screen with no sample route and no exclusion.
  - Verify by temporarily removing one route: the test fails and names the screen.
- [x] 2.4 Implement `LinkTab`, each route's parent chain and owner-only flag, `LinkPlan`, and `StackEdit.of(current, chain)`.
  - Verify with `LinkPlanTest`. It covers the deep-links scenarios for:
    - tabs (own settings, someone else's activity at start, own profile);
    - parent chains (leaderboard, Training zones › History);
    - "segment already open".

## 3. Delivering links (`:app-host`, `composeApp`)

- [x] 3.1 Add `link/LinkRequests` (AppScope: `pending: StateFlow<AppLink?>`, `request(url): Boolean`, `handled(link)` as compare-and-set) and `link/LinkOpener` (SessionScope: resolves handles with `IdentityResolver.resolveHandle`, applies the owner-only rule, falls back to `ProfileScreen(handle)`).
  - Expose them on `AppGraph` and `SessionGraph`, and add `:app-links` to `:app-host`'s dependencies.
  - Verify with jvm tests for: a newer link replacing a pending one, a handle resolved, an unresolvable handle, and someone else's edit link falling back to the activity.
- [x] 3.2 Consume links in `SessionHost.whileOpen`: map `LinkTab` to `TopLevelDestination`, set `selected` without popping to root, then `popTo(keep)` and `goTo` each pushed screen, then `handled`.
  - Verify with host tests through `HostTesting.kt` / `sessionHostOf` covering the presenter-host delta's scenarios: link to another tab, link waiting for a session, chain already in the stack.
- [x] 3.3 Extract `AppShell`'s tab switch into `nav/selectTab(backStack, navigator, viewerDid, dest, toRoot)`, and consume links in `App.kt` `SessionContent` the same way.
  - Verify with a `composeApp` jvmTest `LinkThroughHostTest`, and that the existing `AppShell` tests still pass.
- [x] 3.4 Move the Record and Account notification taps onto `LinkRequests` (`/record`, `/settings/account`): Android `MainActivity`, `IosDataExports.kt`, and the unfinished-recording offer. Delete `RecordScreenRequests` and `AccountScreenRequests`.
  - Verify that the ios-native-shell "Recording notification" and "Unfinished recording" scenarios still pass in the host tests.

## 4. Android and AppView

- [x] 4.1 Give `NativeOAuthConventionPlugin` a `linkHost` (the host of `appviewPublicUrl`) and `GeneratedNetworkConfig.LINK_HOST`, and provide `AppLinks(NetworkConfigs.LINK_HOST)` in AppScope.
  - Verify with a TestKit case in `./gradlew -p build-logic :convention:test` for device mode and an explicit URL.
- [x] 4.2 Add the `linkHost` manifest placeholder, an `autoVerify` https filter with one `pathPrefix` per `topLevelPrefixes` entry, and an `activitytracker` scheme filter. Add `MainActivity.handleLink` in `onCreate` and `onNewIntent`, after the OAuth handling, skipping intents launched from history.
  - Verify with `ManifestLinksTest` (the manifest's prefixes equal `topLevelPrefixes`).
  - Verify on a device: `adb shell am start -W -a android.intent.action.VIEW -d "activitytracker://profile/alice.test"`, cold and warm, and while signed out.
- [x] 4.3 Serve `/.well-known/assetlinks.json` and `/.well-known/apple-app-site-association` from `AppViewConfig` (`ANDROID_APP_PACKAGE`, `ANDROID_CERT_SHA256`, `APPLE_APP_IDS`), each answering 404 when unset. Pass the values through `localStackEnvironment`, and have `scripts/dev.sh` fill in the debug keystore's SHA-256.
  - Verify with `AppViewTest` cases: configured and unconfigured, JSON content type, no redirect, AASA exclusions.
  - Verify with `curl` on the local AppView.

## 5. iOS

- [x] 5.1 Add `IosApp.openLink(url: String, fitDecoder: FitDecoder): Boolean`. In `iOSApp.swift` add `.onOpenURL` and `.onContinueUserActivity(NSUserActivityTypeBrowsingWeb)`, and add an `activitytracker` URL type to `Info.plist`.
  - Verify the iOS build passes its stub check.
  - Verify `xcrun simctl openurl booted "activitytracker://settings/training-zones/history"` from the Search tab lands on the You tab (profile › Settings › Training zones › History), with a simulator screenshot.
- [x] 5.2 Add `iosApp/Config/App.xcconfig` (`#include? "Local.xcconfig"`) as the target's base configuration, a gitignored `Local.xcconfig` that `dev.sh` writes only when `activitytracker.appleTeamId` is set (`DEVELOPMENT_TEAM`, `APP_LINK_HOST`, `CODE_SIGN_ENTITLEMENTS`), and `AppLinks.entitlements` with `applinks:$(APP_LINK_HOST)?mode=developer`.
  - Verify the CLAUDE.md ad-hoc `xcodebuild` command builds unchanged with no `Local.xcconfig`.
- [x] 5.3 Document the custom scheme, the optional team setting and the `simctl openurl` check in `.agents/rules/adaptive-ui.md` and the `local-stack` skill.
  - Verify with `scripts/check-agent-sync.sh`.

## 6. Web

- [x] 6.1 Make the web assets absolute: `/webApp.js` in `index.html`, and Compose resources mapped to `/`-rooted paths in `main.kt`.
  - Verify in the browser pane on `:webApp:wasmJsBrowserDevelopmentRun` that `/settings/account` loads with fonts, strings and images and no failed requests.
- [x] 6.2 At start-up, request `pathname + search` when it isn't `/`. Mirror `LinkRequests.pending` into `sessionStorage`, and request it back on the OAuth callback branch.
  - Verify in the browser pane: load `/segment/{did}/{rkey}` signed out, sign in, and the segment page opens.
- [x] 6.3 Add a `backStackSync` hook to `App`, and a pure `nav/HistorySync` (`Entry`, `diff` giving `Push`, `Pop`, `TabSwitch` or `Replace`) in composeApp commonMain.
  - Verify with `HistorySyncTest` (jvm) covering: push, multi-pop, a settings page swap as `Replace`, a tab switch with restore, and a link's keep-then-push.
- [x] 6.4 Implement `webApp` `BrowserHistorySync`:
  - it waits for no pending link;
  - first frame: `replaceState` for the root, then `pushState` for each record above it;
  - push: `pushState`; in-app pop: `history.go(-n)` with `pendingPops`; tab switch: `pushState`; replace: `replaceState`;
  - `popstate` switches tab and pops to the state's key, or requests the URL as a link;
  - an `expected` stack stops echoes;
  - dispose resets to `/`.

  Verify the web-history scenarios in the browser pane: opening an activity, browser Back and Forward, in-app back, back after a tab switch, reload on `/settings/training-zones/history`, sign out.

## 7. Docs and integration

- [x] 7.1 Add to `.agents/rules/compose-screens.md`, the `new-screen` skill and CLAUDE.md's architecture section that a new screen adds a link route and sample, or an exclusion, in `:app-links` (`LinkCoverageTest`), and that the app's links are described in `ui/deep-links`.
  - Verify with `scripts/check-agent-sync.sh`.
- [x] 7.2 Run `./gradlew check` through the gradle-runner agent, and run `openspec validate add-deep-links --strict`. Both pass.

## Workflow follow-up

- Archive the change with `openspec-archive-change` once it is implemented and reviewed, which syncs `ui/deep-links`, `ui/web-history` and the three deltas into `openspec/specs`.
