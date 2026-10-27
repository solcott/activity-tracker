# Tasks

## 1. Inactive accounts close their spaces (P5)

- [x] 1.1 In `SpaceAccessTest`, add an end-to-end test through `testApplication` that marks bob `suspended` with a Jetstream `#account` event (as `AccountStatusTest.account` does) and checks: alice, his effective follower, is refused his `followers` space; any user is refused his `public` space while he is `deactivated`; the AppView's own DID is still admitted to every tier and `interactions`; after `active: true` alice is admitted again; and a `suspended` alice is still admitted to an active bob's `followers` space; verify it fails before the fix (`./gradlew :server-appview:test --tests "*.SpaceAccessTest"`)
- [x] 1.2 In `SpaceAccess.canRead`, replace `labelledOnly(authority.value, LabelValues.TAKEDOWN)` with `!isAccountServed(authority.value, viewer = null)` and update its comment and the class KDoc to say an inactive or taken-down authority admits no reader; verify `SpaceAccessTest` passes, the existing takedown test included

## 2. Moderators see the subject's gear photos (C9)

- [x] 2.1 In `ModeratorSubjectsTest`, add an end-to-end test: bob writes a Followers `gear.item` with a photo and a Followers activity that lists it to his Followers space, blocks the moderator, and has an open report on the activity; the moderator opens it with `getSubject`, the gear photo link in the answer redirects, a link to his Only me shoes' photo (an item no opened activity lists, signed with the test media key) is refused, and 31 minutes later the bike's link is refused too; verify it fails before the fix (`./gradlew :server-appview:test --tests "*.ModeratorSubjectsTest"`)
- [x] 2.2 In `BlobRedirects.kt`, give `openedByModerator` an optional gear column (an `exists` over `ModerationAccess` inner-joined to `ActivityGear` on the subject URI, matching the viewer, `GearItems.uri` and `MODERATOR_GRANT`) and OR `openedByModerator(viewer, gear = GearItems.uri)` into the gear branch of `visibleMediaOrigin`; update both KDocs; verify `ModeratorSubjectsTest` and `SpaceMediaTest` pass

## 3. Integration

- [x] 3.1 Run `./gradlew :server-appview:test` and `./gradlew :server-appview:check` (ktfmt, detekt) and confirm both pass

## Workflow follow-up

- Commit only when asked, one small commit per finding: P5 (group 1) and C9 (group 2), each with its test.
- After committing, move P5 and C9 in `docs/reviews/2026-10-23-codebase-review.md` to "Fixed since the review" with their commit hashes. That file lives only in the main session's worktree.
- Archive the change once it is committed.
