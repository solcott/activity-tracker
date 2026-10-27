# Design

## Context

**P5.** `SpaceAccess.check` answers the AppView's own DID from `SELF_READS` before `canRead`, so the AppView reads every tier whatever the account's state. `canRead` denies everyone while the authority is blocked by or blocks the user, or while the authority's DID carries `!takedown` (`labelledOnly`, the requirement "Account takedown closes tier spaces" in `moderation/labels`). It never reads `actors.status`, which the indexer sets from Jetstream `#account` events and `accountServed` / `isAccountServed` (`api/Moderation.kt`) combine with the `!takedown` label for every other read.

**C9.** `ModeratorSubjects.load` hydrates an activity as its athlete (`hydrate(rows, author, mediaViewer = moderator)`), so `activityView.gear` lists every item the athlete referenced, at any visibility, and `gearView` signs non-public gear photos for the moderator. The signed-photo route resolves a photo through `visibleMediaOrigin(did, cid, viewer)`. Its activity branch admits `visibleTo(viewer) or openedByModerator(viewer)`, where `openedByModerator` looks for a `moderation_access` row for that moderator and activity URI from the last 30 minutes. Its gear branch admits `visibleTo(viewer, …)` only. `activity_gear` (`activity_uri`, `gear_uri`, origin, listed in) holds each activity's gear references from every origin.

## Goals / Non-Goals

**Goals:**
- `checkUserAccess` closes an athlete's spaces for exactly the accounts `served()` hides, with no new query shape.
- A moderator sees the whole subject `getSubject` showed them, and nothing more, for as long as its own photo links last.

**Non-Goals:**
- Checking the viewer's status on AppView queries (`visibleTo` doesn't, and this change doesn't start).
- Changing what `getSubject` returns.

## Decisions

**`canRead` uses `isAccountServed(authority, viewer = null)`.** It replaces `labelledOnly(authority, TAKEDOWN)` and covers both the status and the label, the same test the signed-photo route uses for avatars. `viewer = null` because the authority's own exemption doesn't apply here: the PDS admits the authority without asking. *Alternative:* add a separate status lookup next to the label check. Rejected: two definitions of "served" would drift.

**The AppView stays admitted to an inactive account's spaces.** `SELF_READS` is answered before `canRead`, so this needs no code. Keeping the sync matches `moderation/account-status` ("keep what it indexed", reactivation "without reindexing"): if the AppView lost access while bob was deactivated, edits made in the meantime would be missing on reactivation. The AppView serves none of it while he's inactive.

**The reader's status is not checked.** A suspended or deactivated reader keeps whatever their tier gives them. The AppView's own views (`visibleTo`) don't check a viewer's status, so denying only the space credential would hide nothing: the same reader still gets the activities through `getActivity`. If viewer status should matter, it belongs in `visibleTo` and `canRead` together, as its own change. In practice a deactivated or taken-down reader's PDS won't mint them credentials anyway.

**The gear branch admits a moderator through the activity they opened.** `openedByModerator` takes an optional gear column (one function rather than two, within detekt's per-file limit). With it, the grant is an `exists` over `moderation_access` joined to `activity_gear` on `subject_uri = activity_uri`, where `moderator_did` is the viewer, `gear_uri` is the outer `GearItems.uri`, and `at` is within `MODERATOR_GRANT`. The gear branch becomes `(visibleTo(…) or openedByModerator(viewer, gear = GearItems.uri)) and origin ≠ repo`, keeping `served(null, …)` from `gearPhotoRows` as the activity branch keeps `servedToAll()`. Every `activity_gear` origin counts, because `getSubject` hydrated as the athlete and so listed gear from every tier. Plain H2-portable SQL: a correlated `EXISTS` with an inner join. *Alternative:* log each gear item in `moderation_access` when `getSubject` answers. Rejected: the audit log records subjects, not what hydrating them touched, and the join already gives the same set.

## Risks / Trade-offs

- [The athlete removes the gear from the activity within the 30 minutes] → The `activity_gear` row goes and the link stops working early. Acceptable: the moderator can open the subject again and sees what it shows now.
- [A moderator who opened one activity can fetch, for 30 minutes, the photo of any gear it lists, even if they later stop reviewing it] → Same window and grant as the activity's own photos, which the spec already accepts.

## Migration Plan

Ships with the AppView. No schema change. An athlete who is inactive when it deploys is closed to readers on their PDS's next callout. Rolling back restores the old answers.

## Open Questions

- A Public subject is answered without an audit entry, so the grant has no row for it, and any non-public gear it lists still shows a refused photo to the moderator. The activity's non-public details are also returned for a Public subject without an entry. Whether a Public subject with non-public parts should be logged is a `getSubject` question, left for a follow-up; it doesn't change this change's specs or tasks.
