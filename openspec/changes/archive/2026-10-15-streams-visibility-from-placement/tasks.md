# Tasks

## 1. Lexicon

- [x] 1.1 Remove `visibility` from `required` and `properties` in `lexicons/app/activitytracker/feed/activityStreams.json`, and reword the record's description so placement alone gives its visibility (public in the repo, the tier in a tier space). Verify the JSON parses and no lexicon text still mentions the field.
- [x] 1.2 Remove `visibility` from `ActivityStreamsRecord` (`core/lexicon/.../app/Records.kt`) and drop the KDoc line about repo records from before spaces. Update `ActivityStreamsSerializationTest` to show a record with no `visibility`, plus one with a stray `visibility` that still decodes. Verify with `./gradlew :core-lexicon:jvmTest`.

## 2. Client

- [x] 2.1 In `PdsActivityStreamsStore` (`core/data/.../activity/ActivityStreamsStore.kt`), stop putting `visibility` into the record in `put`, and call `moveOwn` without the transform in `move` (as `ActivityManager` does for activities). Update the class KDoc to drop the "repo records from before spaces" sentence. `ActivityStreams.visibility` stays and still picks the place.
- [x] 2.2 Fix the `core/data` and `app/presenters` tests that build or assert a record's `visibility` (`ActivityRepositoryTest`, `ActivityVisibilityTest`, `FakeAthletePds`). Assert where the record was written instead. Verify with `./gradlew :core-data:jvmTest :app-presenters:jvmTest`.

## 3. AppView

- [x] 3.1 In `ActivityIndex.kt`'s streams branch, serve streams at `placement.visibility` for both origins and drop the "records from before spaces" comment. Remove `MAX_VISIBILITY_LENGTH` if nothing else uses it. Update the `upsertStreams` KDoc in `StreamsIndex.kt` to say the visibility comes from the placement.
- [x] 3.2 Rework `ActivityStreamsTest`. Keep the Public cases as repo commits, and index the Followers, Mutuals and Only me cases into tier spaces the way `ActivityVisibilityTest` does. Add the spec's two scenarios: a repo record claiming `self` is served as Public, and a `mutuals` space record claiming `public` is served as Mutuals. Fix the class KDoc ("Streams are public records for now"). Check that `SpaceSyncTest.onlyActivitiesAndStreamsAreStored` still holds or adjust it. Verify with `./gradlew :server-appview:test`.

## 4. Docs and backlog

- [x] 4.1 In `.agents/rules/appview.md`, change the streams bullet so the row's visibility is its placement's (Public in the repo, the tier in a space, an unknown tier `self`), not "the record's own field for repo records". Verify with `scripts/check-agent-sync.sh`, since `.claude/rules` mirrors it.
- [x] 4.2 In `BACKLOG.md`, delete the "Older records" bullet from "Activity streams: what's left". Delete the "Activities published before streams have none; there's no backfill" sentence in "More streams" too, if nothing deployed needs it. Verify by grepping `BACKLOG.md` for "before spaces" and "Older records".

## 5. Integration

- [x] 5.1 Run the `lexicon-auditor` agent on `feed.activityStreams`, then run `./gradlew check`. Both pass.
- [x] 5.2 On the local stack, publish one activity with Public charts and one with Only me charts. Check that the repo record has no `visibility`, and that `getActivityStreams` reports Public and Only me respectively (the latter only for the athlete).

## Workflow follow-up

- Archive the change once implemented. The archive syncs the delta into `openspec/specs/feed/activity-visibility/spec.md` and removes "Existing activities stay Public".
