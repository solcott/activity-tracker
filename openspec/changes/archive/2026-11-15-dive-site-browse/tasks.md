# Tasks

## 1. Lexicon and AppView

- [x] 1.1 Add `lexicons/app/activitytracker/feed/searchDiveSiteNames.json` and mirror it (`Queries.kt` `SearchDiveSiteNames`, `Nsid`).
- [x] 1.2 Add `DiveSiteViews.searchNames` (design 2) and route it with optional viewer auth.
- [x] 1.3 Test it: current name not original, proposed only to creator and divers, duplicate left out, blank query, rank order, nearest within a rank, box filter, partial box `InvalidRequest`.

## 2. Client

- [x] 2.1 `DiveSiteRepository`: `areaSites(box, cap)` following cursors, and `namesPage(params)`; tests with `MockEngine`.
- [x] 2.2 `DiveSiteBrowseScreen`, state, events and texts in `feature/divesite/api`; the shared `MapBrowse` controls in `:app-common-presenter`, which segment browse now uses too.
- [x] 2.3 `DiveSiteBrowsePresenter` with presenter tests (area sorted nearest first, too wide makes no request, name search scope, search everywhere, start fallback, open).
- [x] 2.4 `DiveSiteBrowseUi`, samples in `:app-fixtures`, UI test, content-loading conformance, screenshots.
- [x] 2.5 The Search tab's Dive sites mode (presenter, UI, texts, strings) and its tests.

## 3. iOS

- [x] 3.1 `DiveSiteBrowseView` over the presenter, the Search tab's third mode, the screen registry, samples and tests.

## 4. Checks

- [x] 4.1 `./gradlew check` and the iOS build.
