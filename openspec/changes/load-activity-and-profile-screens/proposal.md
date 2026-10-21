# Proposal

## Why

Two repository reads feed most of the screens that still load by hand. `FeedRepository.activity(uri)` feeds six presenters. `ProfileRepository.ownProfile()` feeds five. Each presenter repeats `LaunchedEffect` + `runSuspendCatching` with its own `error`, and most can't retry. Going back to them reloads behind a spinner. Giving these reads outcome versions turns the simple content screens into small edits, and sets up the form screens (`load-form-screens`) and the activity page (`load-activity-detail`).

This is step 2 of moving the rest of the app onto kmp-dataresult, after `error-reasons`.

## What Changes

- **Outcome reads for the shared repository calls:**
  - `FeedRepository.activityOutcome(uri)`. It keeps `activity`'s `updateCached` side effect and its comment-count lookups.
  - `ProfileRepository.ownProfileOutcome()` and `profileOutcome(actor)`. `needsSignIn` stays data on the answer.
  - `FollowRequestsRepository.followRequestsOutcome()`.
- **These content screens move to `ContentState`, retained, with retry:**
  - **RouteMap:** `feed.activity` plus the rewrite overlay. It gains retry, which it has none of today.
  - **MediaGallery:** `feed.activity`. It gains retry and a first-load phase, which it has none of today.
  - **Profile:** the header (`profile(actor)`, with blocks applied) and the activities from `FeedRepository.authorOutcomes`, which already exists. Follow requests stay supplementary.
  - **FollowRequests:** already has retry; gains retention and reasons.
  - **TrainingZones and ThresholdHistory:** `ownProfile()`, read-only views. Both gain retry.
  - **CombinePicker:** two sequential reads (`activity` then `ownViewsStartedBetween`) as one outcome stream.
- **Separate load and action errors.** On these screens, the shared `error` field splits into the load's `ContentState` and an action-only `UiError`, as on the feed.
- **Not in scope:**
  - Form-seeding screens: EditProfile, Zones, SportZones, EditActivity, PrivacyZones (`load-form-screens`).
  - ActivityDetail (`load-activity-detail`).
  - Paged lists (`adopt-library-paging`).
  - Segment screens (`load-segment-screens`).
  - Settings, Import, Record and Manual, whose reads are all supplementary.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `ui/content-loading`: its requirements name "the feed and the segment page". This change extends them to the route map, media gallery, profile, follow requests, training zones, threshold history and combine picker screens. Whichever migration change lands last rewords them to every screen that loads content.

## Impact

- **`core/data`:** `FeedRepository`, `ProfileRepository` and `FollowRequestsRepository` each gain outcome reads beside their existing ones. The old reads stay while ActivityDetail, the forms and import still call them.
- **`app/presenters` and `app/screens`:** the seven presenters and their states listed above, plus their tests:
  - `RouteMapPresenterTest`, `ActivityDetailModerationTest` (media gallery), `ProfilePresenterTest`, `FollowRequestsPresenterTest`, `TrainingZonesPresenterTest`, `ThresholdHistoryPresenterTest`, `CombinePresentersTest`.
  - Profile and the zones views have no load-failure test today, so they gain one.
- **`composeApp`:** the seven UIs render through `ContentStateLayout` and `RefreshStatus`.
- **Depends on:** `error-reasons`, which provides `errorText(UiError)` as the single renderer.
