# Tasks

## 1. Outcome reads in core/data

- [x] 1.1 Add `FeedRepository.activityOutcome(uri)` as `outcomeFlow { activity(uri) }`. Verify with a jvmTest on a `MockEngine` AppView:
  - Loading, then `Data`;
  - an offline failure gives `Error(Network)`;
  - after a success, the cached feed item holds the answered view, so `updateCached` still runs.
- [x] 1.2 Add `ProfileRepository.ownProfileOutcome()` and `profileOutcome(actor)`. Verify in `ProfileRepositoryTest`: success, an XRPC failure classified as `Api`/`Http`, and `needsSignIn` carried on the data.
- [x] 1.3 Add `FollowRequestsRepository.followRequestsOutcome()`. Verify in `FollowRequestsRepositoryTest`: success with `needsSignIn`, and an offline failure.

## 2. Activity screens

Each task changes the screen's State (`content: ContentState<…>`, with `error` for actions only), its presenter (retained, keyed on `loadKey`, with a `Retry` event) and its UI (`ContentStateLayout` plus `RefreshStatus`), as design.md describes. Its presenter test covers:
- loading, then loaded;
- a first-load failure, then Retry, then loaded;
- the state retained across a `RetainedStateRegistry` round trip, as in `SegmentPresenterTest`.

- [x] 2.1 RouteMap: `activityOutcome`, with the rewrite overlay over `content.data` and a new `RouteMapEvent.Retry`. Verify in `RouteMapPresenterTest`, plus a case that a rewrite still changes the shown route.
- [x] 2.2 MediaGallery: `activityOutcome`, with a first-load phase and a new `MediaGalleryEvent.Retry`. Verify in `ActivityDetailModerationTest` (or a new `MediaGalleryPresenterTest`), plus a case that a failed block sets the action `error` and keeps the items.
- [x] 2.3 CombinePicker: one `outcomeFlow` for the activity and its candidates (`CombinePick`). Verify in `CombinePresentersTest`, plus a case that a failed combine sets the action `error` and keeps the candidates, and that an empty answer shows as empty.

## 3. Profile and settings screens

Same per-screen shape and test coverage as group 2.

- [x] 3.1 TrainingZones: `ownProfileOutcome`, with the saved-visibility overlay and a new `TrainingZonesEvent.Retry`. Verify in `TrainingZonesPresenterTest`: its first load-failure test, a visibility save that shows at once and survives a reload, and a failed save that keeps the zones.
- [x] 3.2 ThresholdHistory: `ownProfileOutcome`, with the removed-entries overlay and a new `ThresholdHistoryEvent.Retry`. Verify in `ThresholdHistoryPresenterTest`: its first load-failure test, and that a removal shows at once while a failed removal keeps the groups.
- [x] 3.3 FollowRequests: `followRequestsOutcome`, with the retained answered-DIDs overlay and `needsSignIn` in place of the `SignInAgain` error. Verify in `FollowRequestsPresenterTest`:
  - a request approved and then reloaded while the fake still lists it stays out;
  - a declined one shows under Declined after a reload;
  - `needsSignIn` shows Sign in again;
  - a failed answer keeps the lists.
- [x] 3.4 Profile: the header through `profileOutcome` with `blocks.applyTo`, and the activities through `snapshotFlow { did }` and `authorOutcomes`. Add `UiError.Kind.LoadActivities` and its string. The follow-request count stays quiet. Verify in `ProfilePresenterTest`:
  - its first load-failure test, then Retry;
  - a handle actor loads its activities once the header resolves the DID;
  - an activities-only failure keeps the header and shows `LoadActivities`;
  - a failed follow keeps the profile and sets the action `error`;
  - a failed follow-request count shows no error;
  - the round trip shows the header and activities at once.

## 4. Integration

- [x] 4.1 Verify with `grep` that none of the seven presenters calls `feed.activity`, `ownProfile()`, `profiles.profile` or `followRequests()` any more. List the remaining callers of each old read in this file's follow-up, for `load-form-screens` and `load-activity-detail`.
- [x] 4.2 Run `./gradlew check` and `./gradlew composeStabilityCheck`, and verify both pass (State fields in strict mode, detekt `UseAdaptiveComponents`, ktfmt).
- [x] 4.3 Verify in the web app (`:webApp:wasmJsBrowserDevelopmentRun`), with AppView requests blocked:
  - the route map, media gallery and a profile each show the reason and Retry, and Retry recovers once unblocked;
  - a loaded profile refreshed offline keeps its header and activities and says offline;
  - going back from an activity to a profile shows it at once.
  - Verified in the web app, with AppView requests failed in the page:
    - a profile opened offline says "Couldn't load profile. You're offline." with Retry, and Retry loads it;
    - the media gallery opened offline says "Couldn't load activity. You're offline." with Retry, and Retry loads it;
    - going back from an activity to a loaded profile while offline keeps its header and activities, and says why each couldn't refresh.
  - The route map can't be opened on the web (its preview ignores expand, `ActivityMap.wasmJs.kt`), so its first-load failure and Retry are covered by `RouteMapPresenterTest` only.

## Workflow follow-up

- `load-form-screens` and `load-activity-detail` use `activityOutcome` and `ownProfileOutcome` from this change.
- Callers of the throwing reads left after this change (4.1):
  - Within the seven, by design: `ProfilePresenter`'s follow-request count (`followRequests()`, supplementary, fails quietly) and `CombinePickerPresenter`'s two reads inside one `outcomeFlow` (`feed.activity`).
  - `feed.activity`: `ActivityDetailPresenter` (the page and the sibling count), `EditActivityPresenter`, `CreateSegmentPresenter`, `AddingRecording`; in `core/data`, `ActivityManager` and `CombineSuggestions`.
  - `ownProfile()`: `EditProfilePresenter`, `ZonesPresenter`, `SportZonesPresenter`, `ImportActivityPresenter`; in `core/data`, `TrainingEdit`.
  - `profiles.profile`: `ActivityDetailPresenter` (the viewer's own name for a sent comment).
- Archive the change after review.
