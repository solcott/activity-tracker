# Proposal

## Why

Every screen that loads data rebuilds the same loading, error and retry state by hand. That adds up to about 33 `error` vars, 16 retry counters and roughly 89 `runSuspendCatching` calls across the presenters. Each screen also writes its own spinner, error and Retry UI. Three problems follow:

- **Data isn't retained.** Loaded data lives in plain `remember`, so going back to a screen loads it again behind a spinner.
- **The feed's refresh state is bolted on.** It keeps `refreshing` and `error` flags beside its Room flow.
- **Error text leaks internals.** Nothing records *why* a load failed, so `errorText` shows raw exception messages such as "XRPC 500 …" or Ktor errors.

The `io.github.solcott:dataresult`, `uistate` and `uistate-circuit` libraries model exactly this:

- `Outcome` is one emission: `Loading`, `Data` or `Error`, with the error classified by `DataError`.
- `ContentState` is the folded, stale-while-revalidate view of those emissions, retained in Circuit's registry.

They match this app's Kotlin, Circuit and platform targets.

## What Changes

- **Add the libraries.**
  - Add `io.github.solcott:dataresult`, `uistate` and `uistate-circuit`, resolved from the library's GitHub Packages repository. That repository is filtered so only the `io.github.solcott` group resolves from it.
  - Add an opt-in composite build: a local checkout of kmp-dataresult replaces the published artifacts when the `dataresult.dir` Gradle property points at it.
- **Classify failures in the data layer.** A new boundary in `core/data` turns XRPC, network, timeout and decoding failures into `DataError`.
- **Repositories return outcomes for reads.** They expose `Flow<Outcome<T>>`, starting with the pilot reads:
  - the feed: its Room cache and network refresh become one stream;
  - the segment page: the segment and the viewer's efforts on it.

  Their existing suspend functions stay until every caller has moved.
- **Pilot presenters move to `ContentState`.** The feed and segment page presenters build it with `produceRetainedContentState` and `produceRetainedContentStates`. Their `isLoading`, `refreshing` and `error` fields in State are replaced.
- **Shared loading UI.** One composable draws a `ContentState` in every phase:
  - first-load progress;
  - first-load failure with Retry;
  - content with a refresh indicator;
  - content with a refresh failure.
- **Error text says why.** Read errors show localized text for the failure kind (offline, server error, unreadable response), not raw exception messages.
- **Out of scope:**
  - Writes and other actions stay on `SingleFlight`.
  - Paged lists stay on `CursorPaging`.
  - Other screens move in later changes.

## Capabilities

### New Capabilities
- `ui/content-loading`: How a screen shows content it loads. It covers:
  - first-load progress and failure;
  - keeping shown content through a refresh and through a failed refresh;
  - keeping loaded content when the athlete comes back to a screen;
  - error messages that say why a load failed;
  - retry.

  This change applies it to the feed and the segment page.

### Modified Capabilities
None. The feed filter's caching and error requirements (`feed/feed-filter`) still hold as written.

## Impact

- **Build:**
  - `settings.gradle.kts`: a new repository plus the opt-in `includeBuild`.
  - `gradle/libs.versions.toml`.
  - Dependencies in `:core-data`, `:app-screens` and `:app-presenters`.
  - Every build now needs a GitHub token with `read:packages`, through `gpr.user`/`gpr.key` or `GITHUB_ACTOR`/`GITHUB_TOKEN`. Today the build needs no credentials.
- **Code:**
  - `core/data`: the new outcome boundary, plus outcome-returning reads in `FeedRepository` and `SegmentRepository`.
  - `app/presenters`: `FeedPresenter`, `FeedQuery.kt` and `SegmentPresenter`.
  - `app/screens`: `FeedState` and `SegmentState`.
  - `composeApp`: `FeedUi`, `SegmentUi`, a new shared content-loading composable, and `errorText` in `ui/Labels.kt` plus its string resources.
  - `.agents/rules/compose-screens.md` and `data-and-auth.md`.
- **Library:** may need a release cut, or small fixes found during the pilot. That work happens in the kmp-dataresult repository.
