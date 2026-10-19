# Tasks

Each group leaves `./gradlew check` green and is its own small commit. Run Gradle through the `gradle-runner` agent.

## 1. Visibility sink in `VisibilityFields`

- [x] 1.1 In `app/screens/.../visibility/VisibilityFields.kt`, add `sealed interface VisibilityEvent` (`ActivityChanged(visibility)`, `CategoryChanged(category, visibility)`) and `val eventSink: (VisibilityEvent) -> Unit = {}` on `VisibilityFields`. Verify: `./gradlew :app-screens:compileKotlinJvm` passes.
- [x] 1.2 Give `fieldsOf(...)` a sink parameter. Replace `activityChanged`/`categoryChanged` with `onEvent(VisibilityEvent)` on `NewActivityVisibility`, `EditVisibility` and `DefaultVisibilitySettings`, keeping each one's guards, and have each fill `fields` with its own `::onEvent`. Re-key `DefaultVisibilitySettings`' flight to `VisibilityEvent::class`. Verify: `:app-presenters` compiles.
- [x] 1.3 Remove the `VisibilityChanged`/`CategoryVisibilityChanged` events and their `when` branches from Record, ManualActivity, ImportActivity and EditActivity, and `DefaultVisibilityChanged`/`DefaultCategoryVisibilityChanged` from Settings. Verify: `grep -rn "CategoryVisibilityChanged\|DefaultVisibilityChanged" app composeApp` returns nothing.
- [x] 1.4 Make `ActivityVisibilityForm` send to `fields.eventSink`, dropping `onActivityChange`/`onCategoryChange`, and update `RecordUi`, `ManualActivityUi`, `ImportActivityUi`, `DiveBatchForm` and `EditActivityUi`. Point `SettingsUi`'s `VisibilityPicker`/`DetailVisibilityPicker` at `defaults.eventSink`. Verify: `./gradlew :composeApp:compileKotlinJvm` and `:webApp:compileKotlinWasmJs` pass.
- [x] 1.5 Update the presenter tests that send the removed events (Manual, Import, Edit, Settings and others; about 15 sends) to use `state.visibility.eventSink(VisibilityEvent...)`. Verify: `./gradlew :app-presenters:jvmTest` passes with no assertion changes.
- [x] 1.6 Verify: `./gradlew composeStabilityCheck` passes (the strict mode on `:app-screens` accepts the new lambda field).

## 2. `rememberCursorPaging`

- [x] 2.1 Add `CursorPaging.kt` to `:app-presenters` (root package, next to `SingleFlight.kt`), following design.md Decision 2:
  - `CursorPage<T>(items, cursor)` and a `CursorPaging<T>` holder exposing `items`, `isLoading`, `canLoadMore`, `loaded`, `failure`, `loadMore()`, `retry()` and `update {}`.
  - `rememberCursorPaging(key, enabled, idOf, load)`, with first-page and load-more loading in one `LaunchedEffect` that serves a conflated request channel.

  Verify: compiles.
- [x] 2.2 Add `CursorPagingTest` (in `jvmTest`, using `presenterTestOf`). Verify each of these passes:
  - a page appends, de-duplicated by id;
  - `loadMore` is ignored while loading, after a failure, and with no cursor;
  - a key change cancels a page in flight, and its items never land;
  - a failed first page empties the list;
  - `retry` reloads from the first page;
  - `enabled = false` empties the list;
  - `update` edits the items.
- [x] 2.3 Move `AthleteSearchPresenter` onto it. The key is the trimmed query, with `enabled = trimmed.isNotEmpty()` and the debounce inside the first-page load. Map `failure` to `SearchAthletes` and derive `searched` from `loaded`. Drop the `nextPage` job. Verify: `AthleteSearchPresenterTest` passes unchanged.
- [x] 2.4 Move `FollowListPresenter` onto it, with the loader capturing `subject`. Remove stays a `SingleFlight` write that calls `paging.update` on success, and its failure is the host's own error, which also suppresses `canLoadMore`. Map `failure` by `screen.kind`. Verify: `FollowListPresenterTest` passes. If it pinned the old keep-on-failed-retry behavior, update that assertion and name the edge difference in the commit message.

## 3. Small extractions

- [x] 3.1 Add `rememberApprovesFollowers(profiles, enabled)` in `app/profile`, with a `presenterTestOf` test covering the `enabled` gate and the fallback to `false` on failure. Verify: the new test passes.
- [x] 3.2 Use it in three places:
  - `ActivityDetailPresenter`, with `enabled = isOwn`;
  - `FollowListPresenter`'s `rememberCanRemove`;
  - `EditActivityPresenter`, where `EditVisibility.approvesFollowers` becomes a constructor lambda like `NewActivityVisibility`'s.

  Verify: the detail, follow list and edit presenter tests pass.
- [x] 3.3 Replace `FollowRequestsPresenter.answerError` with `uiErrorOf(UiError.Kind.AnswerFollowRequest, it)`. Verify: `FollowRequestsPresenterTest` passes, including its sign-in-again case.
- [x] 3.4 Route the detail screen's `ToggleLike` through `rememberActivityListEventSink(navigator, activities) { likeError = it }`, clearing `likeError` first. Verify: the `ActivityDetailPresenterTest` like tests pass, including the double-tap-sends-one-like case if present.

## 4. Activity type StateProducer

- [x] 4.1 Add `ActivityTypeField(selected, quickTypes, eventSink)` and `ActivityTypeEvent.Select(type)` in `app/screens/.../record`. Verify: `:app-screens` compiles and passes the strict stability check.
- [x] 4.2 Add `ActivityTypeChoices` (`@Inject` class, takes `RecentActivityTypes`, `@Composable produce(initial, outdoorOnly)`) and the `@Stable ActivityTypeChoice` holder (`selected`, `field`, `select()`) in `:app-presenters` `record/`, reusing `ActivityTypeSaver` and `quickActivityTypes`. Add a `presenterTestOf` test for `select`, the `outdoorOnly` quick row, and the initial value. Verify: the new test passes.
- [x] 4.3 Switch Record, ManualActivity and ImportActivity to it:
  - Inject `ActivityTypeChoices` in place of `recentTypes`.
  - Replace the `type`/`quickTypes` state fields with `type: ActivityTypeField`, and remove each `TypeChanged` event.
  - In Import, call `choice.select(first.type)` when a file is read.

  Verify: `:app-presenters` compiles.
- [x] 4.4 Make `ActivityTypePicker` take an `ActivityTypeField` (keeping `enabled`/`outdoorOnly` as UI args), and update `RecordUi`, `ManualActivityUi` and `ImportActivityUi`, including any other reads of `state.type`. Verify: `:composeApp` JVM and `:webApp` wasm compile.
- [x] 4.5 Update `ManualActivityPresenterTest` (quick-row case) and `ImportActivityPresenterTest` (type change) to read `state.type.quickTypes` and send `state.type.eventSink(ActivityTypeEvent.Select(...))`. Verify: `./gradlew :app-presenters:jvmTest` passes.

## 5. Docs and integration

- [x] 5.1 Add a short subsection to `.agents/rules/compose-screens.md` on shared pieces of a screen, which `.claude/rules/` mirrors by symlink:
  - a sub-state that carries its own `eventSink` (`ActivityDetailsFields`, `VisibilityFields`, `ActivityTypeField`);
  - a host-wrapped shared event when handling it needs the navigator (`ActivityListEvent`);
  - `remember*` producers versus an injected producer class;
  - `rememberCursorPaging` for cursor lists;
  - when to choose a SubCircuit instead (the child needs nothing pushed from the host after creation).

  Verify: `scripts/check-agent-sync.sh` passes.
- [ ] 5.2 Run `./gradlew check` and verify it passes. Then check by hand that these still work in the desktop app (`:desktopApp:hotRun`, following `hot-reload.md`): picking a visibility on manual entry, a default visibility in Settings, an athlete search with load-more, and a type pick on manual entry.

## Workflow follow-up

- Archive the change with `/opsx:archive` once it is implemented and reviewed.
