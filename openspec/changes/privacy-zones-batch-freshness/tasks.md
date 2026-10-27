# Tasks

## 1. Lock the stale routes (C8)

- [x] 1.1 Make `PrivacyZones.published`, `takeStale` and `hasStale` (now `suspend fun hasStale()`) take `mutex`, and update the callers in `ActivityRepository`, `ActivityManager` and `RouteTrimmer`. Verify with a `PrivacyZonesTest` case that publishes many stale routes concurrently on `Dispatchers.Default` while taking them, and checks every route is taken exactly once. Run `./gradlew :core-data:jvmTest --tests "*.PrivacyZonesTest"`.

## 2. Scope the batch read (C7)

- [x] 2.1 Replace the `batch` field with a `BatchRead` coroutine-context element that `withReadOnce` installs, and that `forPublish` looks up with `currentCoroutineContext()`. Verify with a `PrivacyZonesTest` case: a `forPublish` outside a running `withReadOnce`, after the PDS's zones change, trims by the new zones (spec: "Recording saved during an import").
- [x] 2.2 Add a test that a publish outside a batch whose zones can't be read is remembered as stale, even while a batch with a fresh read runs (spec: "Offline publish during an import").
- [x] 2.3 Inside a batch, have `forPublish` return `last` as fresh when it's a `Known` that differs from the batch's trim. Verify with a test: a `save` that adds a zone mid-`withReadOnce`, after which the batch's next `forPublish` trims by the saved zones, so no polyline point is inside the new zone (spec: "Zone saved mid-import").

## 3. Freshness decided at publish

- [x] 3.1 Have `published` count a trim as fresh only when it still equals `last`, and return the effective `PublishTrim`. Pass that value to `SegmentTracks.put` in `ActivityRepository.queue` and in `ActivityManager`'s combine. Verify with a test: get a trim, `save` new zones, call `published`, then check the route is in `takeStale(latest)` and `SegmentTracks.takeStale()` is true when matching is on (spec: "Save lands during a publish").
- [x] 3.2 Update the `PrivacyZones` KDoc and the privacy-zones paragraph in `.agents/rules/data-and-auth.md`: a batch's read is seen only inside `withReadOnce`, and a save during a batch reaches its later publishes. Verify the text matches the code with `scripts/check-agent-sync.sh`.

## 4. Integration

- [x] 4.1 Run `./gradlew :core-data:jvmTest` and `./gradlew check`, and confirm both pass.

## Workflow follow-up

- Mark C7 and C8 fixed in `docs/reviews/2026-10-23-codebase-review.md` with the commit ids.
- Archive the change with `/opsx:archive` once it's reviewed.
