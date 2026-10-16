# Tasks

## 1. Lexicons

- [x] 1.1 Add `lexicons/app/activitytracker/actor/privacyZones.json` (`literal:self`; `zones` ≤ 10 of `#zone` with `latitudeE7`, `longitudeE7`, `radiusMeters` 200–1600, optional `label`; `hideEndsMeters` 0–1600; required `revision` tid) and `actor/routeTrim.json` (`literal:self`; `revision`, `updatedAt`), each description saying it lives only in the `self` space; verify with the `lexicon-change` skill's checklist
- [x] 1.2 Add both collections to `space/athlete.json` and say in its description that the AppView ignores them; update `feed/activity.json`'s `polyline` and record descriptions to say polylines are trimmed by the athlete's privacy zones at every visibility; verify `scripts/dev.sh` republishes them without errors
- [x] 1.3 Mirror both in `core/lexicon` (`PrivacyZonesRecord` with `PrivacyZone`, `RouteTrimRecord`, `RKEY = "self"`) and add `Nsid.ACTOR_PRIVACY_ZONES` and `Nsid.ACTOR_ROUTE_TRIM`, kept out of `indexedCollections`; verify with an `AtJson` round-trip test in `core/lexicon` (`./gradlew :core-lexicon:jvmTest`)
- [x] 1.4 AppView: a test that drives `self`-space `actor.privacyZones` and `actor.routeTrim` puts through the `Indexer` and checks nothing is stored, like the `feed.activityTrack` one; verify `./gradlew :server-appview:test`
- [x] 1.5 Run the `lexicon-auditor` agent on the two new NSIDs and the changed descriptions and fix any mismatch it reports

## 2. Trimming math

- [x] 2.1 Add `TrackMath.trim(points, circles, endsMeters)` per design (hidden by circle or by distance from either end, boundary points interpolated, middle passes joined by a chord) with a `PrivacyCircle` type in `core/location`; verify with `TrackMathTest` cases: start and end inside a zone, a middle pass, hide-ends only, both, everything hidden, a track entirely outside, fewer than two points
- [x] 2.2 Add the offset helper (uniform in the disk of radius `r/2`, injectable `Random`) and E7 conversion; verify with tests that every offset of 1,000 seeded draws is within `r/2` and that E7 round-trips to within 1 cm

## 3. Privacy zones store

- [x] 3.1 Add `PrivacyZones` (session-scoped, `core/data`) with `forPublish()` returning `Known`/`Unknown`, `withReadOnce`, `current`, `refresh`, `save` (offsets only new zones, new revision, direct `putRecord`, `ScopeMissingError` without `canWriteToSpaces(actor.privacyZones)`) and `add` (refuses an 11th zone), clamping radii and the zone count on read; verify with a `core/data` jvm test against the fake PDS covering: no record is none, an undecodable record is `Unknown`, a failed read after a good one returns the last read, a session without `space:` is none, a save offline throws and keeps the earlier zones, the chosen point appears in no written record
- [x] 3.2 Track, per session, rkeys trimmed with a revision older than the latest read (or `Unknown`) and hand them to the trimmer on a newer read; verify with a test that an activity published before the first read gets a trimmed polyline once the read succeeds

## 4. Trim on write

- [x] 4.1 Change `ActivityDraft` to carry `route` instead of a built polyline and make `toRecord` take the polyline; keep `polylineOf(samples)` for the import preview; update `fromRecording`, `fromImport` and their callers; verify `./gradlew :core-data:jvmTest :app-presenters:jvmTest` compiles and passes
- [x] 4.2 Build the trimmed polyline in `ActivityRepository.queue` and `mergeInto` from `PrivacyZones.forPublish()` (no polyline when `Unknown`), reading once in `publishAll`; verify with repository tests: a Public and an Only me recording starting in a zone both get trimmed polylines, totals and streams and the `self` track are unchanged, a fully hidden route has no polyline, an offline-before-read publish has none
- [x] 4.3 Make `ActivityManager.edit` and `mergeInto` start from the outbox's last pending `feed.activity` put for the rkey when there is one; verify with a test that editing a title after a queued re-trim keeps the trimmed polyline

## 5. Re-trim sweep

- [x] 5.1 Add `ActivityTrackStore.read(activity)` (`getBlob` from `self` + `GpxParser`, null when absent or unreadable) and a fake for tests; verify with a `PdsActivityTrackStore` test that a written track reads back to the same points
- [x] 5.2 Add `RouteTrimmer` and `ActivityRepository.repairRoutes()` per design (compare revisions, list activities at every place and tracks in `self`, skip queued rkeys, rewrite only differing polylines through the outbox and `feed.rewrites`, drain, then write `routeTrim`); verify with tests: adding a zone re-trims Public and Only me activities, removing one restores the hidden part, manual entries aren't rewritten, nothing but `polyline` changes, an interrupted sweep (drain fails) leaves the marker and a second run finishes, a run with matching revisions does nothing
- [x] 5.3 Run `repairRoutes` once a session from `FeedPresenter`'s `LaunchedEffect` after `repairZones`; verify with a `FeedPresenterTest` case that the feed loads while the sweep is still running

## 6. UI

- [x] 6.1 Scaffold `PrivacyZonesScreen` with the `new-screen` skill (zones list with label `TextFieldState`, radius picker, delete; hide-start/end picker; "Add at current location" only when `LocationTracker.isSupported`; a note that copies others already hold aren't changed), launching the re-trim after each save; verify with a presenter test (save, delete, failed save shows the error and keeps the old zones, no current-location option with `UnsupportedLocationTracker`) and `./gradlew composeStabilityCheck`
- [x] 6.2 Link it from Settings with a one-line summary (zone count, hide-start/end); verify in the desktop app with Compose Hot Reload (screenshot of Settings and the new screen)
- [x] 6.3 Own activity detail: load the full track apart from the activity into `State.ownRoute` and draw `ownRoute ?: polyline`; add "Hide this start" / "Hide this end" (400 m zone at the track's first or last point) when the track is readable; verify with `ActivityDetailPresenterTest` cases (own activity uses the track, someone else's and an unreadable track use the polyline, hide-start adds a zone) and a hot-reload screenshot

## 7. Docs and integration

- [x] 7.1 Update `.agents/rules/data-and-auth.md` (privacy zones store, trimming in the repository, the re-trim sweep and its marker), `CLAUDE.md`'s architecture note on `self`-space settings if it names them, and remove the privacy-zones item from `BACKLOG.md`; verify `scripts/check-agent-sync.sh` passes
- [x] 7.2 End to end on the local stack (`scripts/dev.sh --seed alice`): add a zone, publish a Public activity that starts in it, check the repo record's polyline and the AppView's `getActivity` both start on the boundary, then remove the zone and check the polyline comes back; then run `./gradlew check` and verify it passes
