# Proposal

## Why

Two edges of the AppView's privacy rules don't match the rest of it. Both were findings in the 2026-10-23 codebase review.

- **P5:** `checkUserAccess` (`SpaceAccess.canRead`) closes an athlete's tier spaces while their DID carries `!takedown`, but it ignores their hosting status (`actors.status`). Everywhere else, `served()` hides a `takendown`, `suspended` or `deactivated` account from everyone. So while bob is suspended, his `followers` and `mutuals` spaces still admit his followers and mutuals, though the AppView serves none of his content.
- **C9:** a moderator's photo links from `getSubject` work for an activity's own photos, because `openedByModerator` lets the audit log grant them. The same answer also shows the activity's gear, with photo links signed for the moderator, but the gear branch of the photo redirect (`visibleMediaOrigin`) checks only `visibleTo(moderator)`. So a reported Followers activity's Followers bike shows a broken photo to the moderator reviewing it.

## What Changes

- **An inactive athlete's spaces admit no reader.** While the AppView holds a non-active hosting status for the space's authority (`takendown`, `suspended` or `deactivated`), `checkUserAccess` denies `read` on every key to every user. The AppView itself is still admitted, so it keeps what it indexed and serves it again on reactivation without reindexing. This is what `!takedown` already does.
- **The reader's own status doesn't change the answer.** A suspended or deactivated reader is decided by tier as before. The AppView doesn't check a viewer's status anywhere else either, and denying them only here would protect nothing they can't read through the AppView.
- **The moderator grant covers the subject's gear photos.** A moderator who opened an activity through `getSubject` may fetch the photo of any gear item that activity lists, within the same 30-minute window as its own photos, at any visibility. Gear on activities they didn't open stays refused.

Out of scope: checking a viewer's account status on the AppView's own queries, and a Public subject's non-public gear (see design.md, Open Questions).

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `moderation/account-status`: an inactive account's tier spaces are closed to readers, as its content already is.
- `moderation/private-subjects`: moderators' short-lived photo links cover the gear items on the subject they opened.

## Impact

- **server/appview**: `api/SpaceAccess.kt` (`canRead` checks the authority with `isAccountServed` instead of the `!takedown` label alone) and `api/BlobRedirects.kt` (the gear branch of `visibleMediaOrigin` admits a moderator through the audit log and `activity_gear`). No migration, lexicon or client change.
- **Tests**: `SpaceAccessTest` and `ModeratorSubjectsTest`, both through `testApplication`.
