# Design

## Context

See proposal.md (Why) for the motivation, and `specs/ui/content-loading` for the behavior. This section covers only the current state that shapes the approach.

**Repositories.** They expose throwing `suspend fun`s for network reads, for example `SegmentRepository.segment(uri)` and `efforts(segment, …)`. The feed is the one read backed by Room:

- `FeedRepository.timeline/authorActivities` return a `Flow<List<ActivityView>>` from the cache.
- `refreshTimeline/refreshAuthor` fetch and replace the cached first page.
- `FeedQuery.kt` in `:app-presenters` picks the pair for the Mine/Following scope.

**Presenters.** They catch failures with `runSuspendCatching` and turn them into `UiError(kind, detail = cause.message)`. `errorText` then prefers `detail`, which is how raw text reaches the screen.

**Failures.**
- XRPC failures are one type: `XrpcException(status, error, message)` in `core/atproto`. A non-XRPC error body becomes `error = "HttpError"`.
- Ktor transport failures, such as `kotlinx.io.IOException` and `HttpRequestTimeoutException`, pass through unwrapped.

**Library.** Version 0.1.0-SNAPSHOT on GitHub Packages. Its `uistate-circuit` folds `Flow<Outcome<T>>` into a `ContentState<T>` held by `produceRetainedState`. A local checkout is at `~/projects/kmp-dataresult`.

## Goals / Non-Goals

**Goals:**
- One read pattern from repository to UI, proven on the feed (a Room cache with a network refresh) and the segment page (two network sources, one of them optional).
- Failure classification lives in exactly one place, the data layer.
- Library changes can be iterated against this app without publishing.

**Non-Goals:**
- Migrating other screens, or removing `UiError`, which actions still use.
- Paging: `CursorPaging` keeps its own failure handling.
- An outcome type for writes.
- A detekt rule requiring the pattern. That can come once more screens use it.

## Decisions

### Repositories return `Flow<Outcome<T>>`, not presenters adapting throwing calls
The data layer is the only place that knows whether a value came from cache or network, and whether a fetch is still coming, which is what the cache-miss rule needs. Adapting in presenters would keep `core/data` untouched. But every presenter would then repeat the classification and the Room/refresh merge, and that duplication is the problem being fixed.

The new functions sit beside the old ones, for example `timelineOutcomes` next to `timeline` and `segmentOutcome` next to `segment`. Callers move one screen at a time. Each old function is deleted once nothing calls it.

### One failure boundary in `core/data`
`outcomeOf { … }` (a suspend function returning `Outcome<T>`) and `outcomeFlow { … }` (a `Flow` that emits `Loading` and then the answer) wrap a block and classify what it throws:

| Thrown | `DataError` |
|---|---|
| `XrpcException` with `error == "HttpError"` | `Http(status)` |
| other `XrpcException` | `Api(listOf(message ?: error), code = error)` |
| `kotlinx.io.IOException`, which Ktor's request, connect and socket timeouts and Darwin's request failures all extend | `Network` |
| on the web, Ktor's `Error("Fail to fetch")` caused by a `JsError` (a rejected `fetch`), through the `isPlatformNetworkFailure` expect/actual | `Network` |
| `SerializationException` (XRPC bodies decode through `AtJson`) | `Serialization` |
| anything else | `Unknown(cause)` |

- `CancellationException` is rethrown, the same rule as `runSuspendCatching`.
- Network failures carry `Origin.Network`. A failure reading Room carries `Origin.Cache`.
- **Why `core/data` and not `core/atproto`:** `core/atproto` is the hand-written transport and should not take on a UI-facing vocabulary.

### The feed: one stream from cache and refresh
`timelineOutcomes(filter)` and `authorOutcomes(did, filter)` merge two things in a `channelFlow`:

- **The refresh,** launched when the flow is collected. It sends `Loading`, then `Error` if the refresh fails.
- **The Room observer.** Its emissions become `Data`:
  - `Origin.Cache` until the refresh succeeds, `Origin.Network` after.
  - An empty read is held back until the refresh succeeds, both while it's in flight and after it fails. That is the library's cache-miss rule, and it gives the "progress before the first answer" and "first-load failure" requirements: a failed first visit shows the failure, never an empty list.
  - A cached read after a failure is followed by the failure again, so a slow Room read can't settle a failed refresh.
  - Once the refresh succeeds, the cache is read once more and sent as `Network`, since Room may not re-read a write that changed no rows.

`FeedQuery.kt` keeps choosing between the two by scope. It also runs the outbox drain (`activities.sync()`) at the start of the first and of each explicit refresh, as today. The drain sits inside the stream, so its failure surfaces as the feed's error, as it does now.

**In the presenter:**
- `key(mine, query) { produceRetainedContentState(persistentListOf(), refreshKey) { … } }`.
- Keying on the filter gives each filter its own state. That keeps `feed/feed-filter` true: a new filter never shows the old one's activities, and its own cached page shows at once.
- `refreshKey` is a producer key, so pull-to-refresh restarts the stream without discarding the content. The stream's `Loading` is what puts up the indicator.

### The segment page: two sources combined, the optional one ignored on failure
`SegmentRepository.segmentOutcome(uri)` and `effortsOutcome(uri)` are `outcomeFlow`s. The presenter folds `combineOutcomes(segment, efforts)` with `produceRetainedContentStates(contentStatesOf(null, persistentListOf()), loadKey)`.

- The page's loading, error and retry come from the segment's state alone.
- The efforts state is shown when it has data, and its error is never shown. That keeps the "supplementary content fails quietly" requirement.
- The star stays a `LaunchedEffect` plus `SingleFlight`. It is an action-backed toggle, not content.
- The segment's `T` is nullable (`SegmentView?`) because the placeholder has to exist before the first answer. The UI uses `hasLoaded`, never `!= null`, to choose a phase.

### `ContentState` lives in State, rendered by one shared composable
- `FeedState` and `SegmentState` replace `isLoading`, `refreshing` and `error` with `ContentState` fields. `:app-screens` takes `api(uistate)`.
- `ContentState` is `@Immutable` through the runtime-annotation artifact both projects already use. With `ImmutableList` data, `composeStabilityCheck` stays green.
- A new `ContentStateLayout` in `composeApp/.../ui/` draws the four phases from the spec:
  - progress before `hasLoaded`;
  - error with Retry before `hasLoaded`;
  - content, plus a refresh indicator while loading;
  - content, plus an inline failure with Retry after a failed refresh.

  The feed passes its pull-to-refresh through `AppPullToRefresh`. The segment page uses `AppProgressIndicator` for refreshes.

### Error text: what failed, then why
A new `loadErrorText(kind: UiError.Kind, error: DataError)` sits beside `errorText`. It uses the existing localized text for the kind, such as "Couldn't load segment", and adds a reason sentence:

| `DataError` | Reason shown |
|---|---|
| `Network` | offline |
| `Http` with a status ≥ 500, or `Api` with code `InternalServerError` | server problem |
| `Serialization` | couldn't read the response |
| anything else | no reason, the kind's text only |

Raw `message`, `code` and status are never shown. `errorText(UiError)` is left alone for actions.

### Building against a local library checkout
`settings.gradle.kts` includes the library build when `providers.gradleProperty("dataresult.dir")` names an existing directory. Gradle then substitutes `io.github.solcott:*` with the checkout's projects.

- The property is set per machine in `~/.gradle/gradle.properties` with an absolute path. A relative path breaks in `.claude/worktrees/…`.
- Without the property, the GitHub Packages repository is used. It is filtered with `content { includeGroup("io.github.solcott") }`, with credentials from `gpr.user`/`gpr.key`, falling back to `GITHUB_ACTOR`/`GITHUB_TOKEN` (the same block as Recipes).
- **Alternative:** `publishToMavenLocal` plus a filtered `mavenLocal()`. It adds a publish step to every library edit, so it is only the fallback, for the case where the composite build fails.

### Pin a release before the change is done
Development may use `0.1.0-SNAPSHOT` or the composite build. The change finishes on a non-snapshot version, cut from the library once the pilot's library fixes, if any, have landed.

A snapshot is replaced on every green push to the library's `main`. Gradle caches it for 24 hours. So the same commit of this app could build against different library code from one day to the next.

## Risks / Trade-offs

- **[Every build needs a GitHub token]** GitHub Packages authenticates even public reads. → Document it in README's setup. The `content` filter keeps a bad token from breaking other dependencies. Moving the library to Maven Central later removes the requirement without changing code here.
- **[Two included builds named `build-logic`]** The library's `pluginManagement` includes its own `build-logic`, as this repo does. → Check it first, as task 1. Fall back to `mavenLocal` if Gradle rejects the duplicate name.
- **[`runtime-retain` next to Compose Multiplatform 1.12.2]** `uistate-compose` pulls `androidx.compose.runtime:runtime-retain`. → Build iOS (`embedAndSignAppleFrameworkForXcode`) and wasmJs early. Circuit already brings the androidx runtime, so this is likely fine.
- **[Paused records re-collect]** Circuit drops a paused record's producer and starts it again on resume. A feed tab the athlete returns to will refresh again. → That is acceptable: content stays on screen under the indicator. Confirm the per-tab back stacks in `AppShell` don't refresh hidden tabs in a loop.
- **[`DataError` is closed]** App-specific read failures, such as `SCOPE_MISSING` or `NotFound`, land in `Api(code = …)` and are matched by code string. → Only a few reads need it, and none are in the pilot. Revisit with the library, through an extension point, if a migrated screen needs typed domain failures.
- **[Two APIs per pilot repository during migration]** → Delete the old function in the change that moves its last caller.

## Migration Plan

- Nothing is persisted or served differently. Room schemas, lexicons and the AppView are untouched.
- Rollback is reverting the change.
- Other screens move in later changes, each extending `ui/content-loading` to cover the screen it moves.
