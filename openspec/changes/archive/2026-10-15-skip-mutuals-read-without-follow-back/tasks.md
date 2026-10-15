## 1. Lexicon and model

- [x] 1.1 Add `followedBy` (at-uri, optional) to `#viewerState` in `lexicons/app/activitytracker/actor/defs.json`
- [x] 1.2 Add `followedBy: AtUri?` to `ProfileViewerState` in `core/lexicon`, and cover it in `RecordSerializationTest`

## 2. AppView

- [x] 2.1 Fill `followedBy` in `viewerState` from the athlete's effective follow of the viewer (`effectiveFollow()`)
- [x] 2.2 Tests: `followedBy` set for an open-policy viewer, absent while pending the viewer's approval, set once approved, absent with no follow-back

## 3. Client

- [x] 3.1 `ProfileRepository.withHiddenDetails`: read `mutuals` only with an effective `following` and `followedBy`; `followers` alone with `following` alone; every tier on your own profile
- [x] 3.2 Confirm `PendingFollows` keeps `followedBy` when it lays a pending follow or unfollow over the view
- [x] 3.3 jvm test: a one-way follower requests no `mutuals` credential and still reads `followers`
- [x] 3.4 Update the data rule (`.agents/rules/data-and-auth.md`) where it says which tiers a follower reads

## 4. Verify

- [x] 4.1 `./gradlew :server-appview:test`, `./gradlew :core-data:jvmTest`, `./gradlew check`
- [x] 4.2 lexicon-auditor clean
