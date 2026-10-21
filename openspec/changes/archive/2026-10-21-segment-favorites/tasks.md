# Tasks

## 1. Lexicon

- [x] 1.1 Add `lexicons/app/activitytracker/feed/segmentStar.json` (`subject` strongRef, `createdAt`, key `any`, description saying it lives only in `self` and the AppView never stores it); verify the JSON parses and matches neighbouring record lexicons' shape
- [x] 1.2 Add `app.activitytracker.feed.segmentStar` to `space/athlete.json` `collections` and its description (self-only, ignored by the AppView); verify `scripts/check-agent-sync.sh` is unaffected and the docker PDS still resolves the declaration
- [x] 1.3 Add `Nsid.FEED_SEGMENT_STAR` (not in `indexedCollections`) and `SegmentStarRecord` in `Records.kt`; verify with a round-trip `AtJson` test in `SegmentRecordsTest`
- [x] 1.4 Add `segmentStarRkey(uri)` to `core/atproto/.../RecordKeys.kt`, sharing the hash helper with `commentHideRkey`; verify with a test that it's stable, valid as an rkey, and differs per URI
- [x] 1.5 Run the `lexicon-auditor` agent on `feed.segmentStar` and `space.athlete`; verify it reports no mismatches

## 2. AppView

- [x] 2.1 Add a space-sync test that a `self` op writing `feed.segmentStar` stores nothing and that `feed.getSegment`'s view carries no star state; verify with `./gradlew :server-appview:test`

## 3. Data

- [x] 3.1 Add `SegmentStars` (SessionScope) in `core/data/.../segments/`: `canUse`, `isStarred`, paged `starred`, `setStarred` through `OutboxWriter` to `self` with local overrides and `outbox.drain()`; verify with `SegmentStarsTest` (jvm) covering star, unstar, same rkey on repeat, offline queueing, overrides in `starred`, `NoAccess` → null, undecodable records skipped, and an old-scope session
- [x] 3.2 Run `./gradlew :core-data:jvmTest`; verify it passes

## 4. Segment screen

- [x] 4.1 Add `starred: Boolean?`, `canStar` and `SegmentEvent.ToggleStar` to `SegmentScreen.kt`, load the star in `SegmentPresenter` without blocking the segment, and toggle it; verify with `SegmentPresenterTest` cases for starred, unstarred, unreadable (null), toggle, and no grant (control hidden)
- [x] 4.2 Add the star control to `SegmentUi` using `ui/adaptive` components, hidden when `canStar` is false; verify `./gradlew composeStabilityCheck` and detekt pass

## 5. Starred list

- [x] 5.1 Scaffold `StarredSegmentsScreen` across `:app-screens`, `:app-presenters` and `composeApp` with the `new-screen` skill; the presenter pages stars and resolves each through `SegmentRepository.segment` (at most 4 at once), with unavailable rows for `NotFound`; verify with presenter tests for newest-first order, an unavailable segment that can be unstarred, a load failure with retry, an empty list, and "sign in again" without the grant
- [x] 5.2 Add `ProfileEvent.OpenStarredSegments` and a "Starred segments" row on the athlete's own profile only; verify with a profile presenter test that it navigates for self and isn't offered for others

## 6. Integration

- [x] 6.1 Run `./gradlew check`; verify it passes
- [x] 6.2 Against the local stack (`scripts/dev.sh --seed alice`, `docker/scripts/seed-segment.sh`), star a segment in the desktop app, open it from the profile list, and unstar it, with screenshots from the hot-reload MCP; verify the record appears in and leaves alice's `self` space and that the AppView's database holds nothing from it

## Workflow follow-up

- Archive the change with `/opsx:archive` after review.
