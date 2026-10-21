# Tasks

## 1. Dependency wiring

- [x] 1.1 Add the GitHub Packages repository to `settings.gradle.kts`. Filter it to `io.github.solcott`, with credentials from `gpr.user`/`gpr.key`, falling back to `GITHUB_ACTOR`/`GITHUB_TOKEN`. Verify with `./gradlew help` that the build still configures.
- [x] 1.2 Add the opt-in `includeBuild` on the `dataresult.dir` Gradle property. Verify with it set and unset. If Gradle rejects two included builds named `build-logic`, switch to the filtered `mavenLocal()` fallback from design.md and record that there.
- [x] 1.3 Add `dataresult`, `uistate` and `uistate-circuit` to `gradle/libs.versions.toml`. Wire them: `api` in `:core-data` and `:app-screens`, `implementation` in `:app-presenters`. Verify that `./gradlew :composeApp:compileKotlinJvm`, the wasmJs compile and `:composeApp:embedAndSignAppleFrameworkForXcode` (or the iOS simulator link) all resolve and build.
- [x] 1.4 Document the token requirement and the `dataresult.dir` property in README's local setup. Verify that a fresh shell with only the documented properties builds.

## 2. Failure boundary in core/data

- [x] 2.1 Add `outcomeOf` and `outcomeFlow`, with the `DataError` classification table from design.md. `CancellationException` must be rethrown. Verify with jvmTest cases covering each row: XRPC `HttpError`, an XRPC error name, an IOException, a request timeout, a serialization failure, unknown, and cancellation.

## 3. Feed

- [x] 3.1 Add `FeedRepository.timelineOutcomes` and `authorOutcomes`, which merge the refresh with the Room observer as design.md describes. Verify by extending `FilteredFeedTest` (or a sibling test):
  - a cached page emits `Data(Cache)` and then `Data(Network)`;
  - an empty cache emits nothing until the refresh answers;
  - a failed refresh emits `Error` and keeps the cached `Data`.
- [x] 3.2 Move `FeedQuery.kt` to the outcome streams, with the outbox drain inside the stream. Change `FeedPresenter` to `key(mine, query) { produceRetainedContentState(…, refreshKey) }`. Replace `refreshing` and `error` in `FeedState` with a `ContentState`. Keep the segments-prompt save error for actions. Verify `FeedPresenterTest`:
  - cached, then refreshing, then settled;
  - a failed refresh keeps the activities;
  - a first-load failure, then Retry;
  - a filter change never shows the previous filter's activities.
- [x] 3.3 Remove `timeline`/`refreshTimeline`/`authorActivities`/`refreshAuthor` from `FeedRepository` only if nothing else calls them (the Profile presenter does). Otherwise leave them and note them for the Profile migration. Verify with `grep`.

## 4. Segment page

- [x] 4.1 Add `SegmentRepository.segmentOutcome` and `effortsOutcome`, built on `outcomeFlow`. Verify with a jvmTest using a `MockEngine` AppView: success, an XRPC error, and an offline failure.
- [x] 4.2 Move `SegmentPresenter` to `combineOutcomes` and `produceRetainedContentStates`. The page's phase comes from the segment's state only. Efforts errors are never shown. The star stays on `SingleFlight`. Replace `isLoading` and `error` in `SegmentState`. Verify with a new `SegmentPresenterTest`:
  - loading, then loaded;
  - a first-load failure, then Retry, then loaded;
  - an efforts failure shows the segment with no error;
  - the state is retained across a navigation round trip.

## 5. Shared UI and error text

- [x] 5.1 Add `ContentStateLayout` in `composeApp/.../ui/`, using `AppProgressIndicator` and `AppButton`. It draws the four phases. Verify with `./gradlew check`, so detekt's `UseAdaptiveComponents` passes, and with `composeStabilityCheck`.
- [x] 5.2 Add `loadErrorText(kind, DataError)` and the reason strings (offline, server problem, unreadable response) to `strings.xml`. Verify with a test or preview check that no status code, error name or exception message reaches the text.
- [x] 5.3 Render `FeedUi` and `SegmentUi` through `ContentStateLayout`, with the feed keeping `AppPullToRefresh`. Verify in the desktop app (`:desktopApp:hotRun --auto`, with screenshots through the hot-reload MCP):
  - first load;
  - an offline refresh keeps the activities and says offline;
  - going back from an activity to the segment page shows it at once.
  - Verified in the web app rather than the desktop one (same shared UI; the hot-reload MCP server did not connect): first load, an offline refresh keeps the activities and says offline, the segment page shows at once after going back and keeps its content through a failed reload, Retry recovers.

## 6. Rules and docs

- [x] 6.1 Describe the read pattern:
  - in `.agents/rules/data-and-auth.md`: repository reads return `Flow<Outcome<T>>` through the boundary;
  - in `.agents/rules/compose-screens.md`: presenters use `produceRetained*ContentState`, State holds `ContentState`, and the UI uses `ContentStateLayout`; actions stay on `SingleFlight`.

  Verify that `scripts/check-agent-sync.sh` passes.

## 7. Integration

- [x] 7.1 Run `./gradlew check` and `./gradlew composeStabilityCheck`, and verify both pass.
- [x] 7.2 Run the web (`:webApp:wasmJsBrowserDevelopmentRun`) and iOS simulator builds. Verify that the feed and segment page load, refresh and go back without a full-screen spinner. Confirm that switching `AppShell` tabs doesn't refresh hidden tabs in a loop.
  - Web: feed loads; an offline refresh keeps the activities and says offline; the segment page shows at once after going back, keeps its content through a failed reload, and Retry recovers; Feed to Search to Feed fetches once and a hidden tab stays quiet.
  - iOS simulator (iPhone 16 Pro, iOS 18.4): feed loads after sign-in; pull to refresh shows the native spinner over the activities, which stay listed; the segment page shows its first-load spinner, then the segment and efforts; going back from an effort's activity shows it during the pop animation, with no spinner. Offline was not forced on iOS: Darwin reports transport failures as `IOException`s (`DarwinHttpRequestException`), which the JVM tests cover.

## Workflow follow-up

- When kmp-dataresult gets a non-snapshot release, pin it in `libs.versions.toml` in place of `0.1.0-SNAPSHOT`. Not part of this change.
- Migrate the remaining screens in later changes. Profile goes first, since it shares the feed's Room stream. Each extends `ui/content-loading`.
- Archive the change after review.
