# Proposal

## Why

An athlete who stops using Activity Tracker has no way to take back what the app put in their atproto account: activities in the repo and in tier spaces, profile details, gear, follows, likes, comments, segment tracks, segments and dive sites. Deleting the whole account at the PDS isn't an answer, because that account may also be their Bluesky identity. This is the third of the account-management changes. It follows `account-info` (the Account page, where Leave goes) and `data-export` (the safety net offered first).

## What Changes

- New **Leave Activity Tracker** action on the Account page. Before confirming, it shows the athlete what will be deleted, what stays (the atproto account, handle and Bluesky data), and how many of their segments and dive sites other athletes use, which stay on the map without their name. It offers an export first, won't start while an export is running, and offers to save a finished export before leaving deletes it.
- Leaving runs as one resumable job on the device:
  1. Record locally that the account is leaving.
  2. Discard this device's outbox and cancel any import or recording.
  3. Delete each of the athlete's spaces, `self` first.
  4. Delete every `app.activitytracker.*` record from the repo, in paced `applyWrites` batches.
  5. Clear the account's local data and sign out.

  If the app is interrupted, it resumes the leave the next time it starts.
- Clients can delete a space: `com.atproto.space.deleteSpace` in `core/lexicon` and `SpaceClient`. **BREAKING** (sign-in): the OAuth scope gains `manage=delete`, so existing grants show as not current on the Account page and need **Sign in again** before leaving.
- **Other devices stop instead of recreating data.** A device that has used its account's `self` space and finds it gone treats the account as having left elsewhere. It sends nothing, and never recreates a space on its own. With nothing pending it signs out and says why. With pending work, the athlete chooses **Discard and sign out** or **Rejoin**.
- **Handoff of shared segments and dive sites.** When the AppView sees an `active` segment or site deleted, on any path (a record delete, a leave, an account deleted at the PDS), and the segment importer didn't create it, it keeps a handoff. The handoff holds the line or centre, the sport group or radius, and the current name and name history. That's all public data, nothing about riders. The importer lists pending handoffs (`importer.getHandoffs`) and republishes each one from `SEGMENT_IMPORTER_DID` with a new `handoff` command, as `source` `athlete`, `CC0-1.0`, with no attribution. The republished copy starts `active`, the matcher rebuilds efforts and visits from stored segment tracks (Mayors boards follow from those), the adopted names and history carry over, and the old URI resolves to its successor.
- **BREAKING** (behavior): deleting an active segment or site no longer removes it for good. It comes back from the importer, and moderation labels remain the way to remove one that shouldn't exist. The app has no in-app delete for segments or sites, so the leave screen is where athletes are told.
- `feed.getSegment` and `feed.getDiveSite` asked for a handed-off URI return the successor's view. Clients rewrite a segment star whose segment now has another URI.

Out of scope: deleting the atproto account (the PDS's job), signing other devices out remotely, multiple accounts, and an opt-out from the handoff.

## Capabilities

### New Capabilities
- `auth/leaving`: the Leave Activity Tracker flow: the summary and confirmation, its order, resuming after an interruption, what's deleted and what stays, and how other signed-in devices detect a leave and stop.
- `segments/segment-handoffs`: an active, non-importer segment deleted on any path is kept as a handoff, which the importer republishes. Its names carry over, the old URI resolves to the successor, and stars follow it.
- `dive-sites/site-handoffs`: active, non-importer dive sites are handed off the way segments are.

### Modified Capabilities
- `spaces/client-protocol`: the OAuth scope adds `manage=delete`, and owners can delete their own spaces.
- `segments/segment-records`: deleting an active, non-importer segment also stages a handoff, and looking up a handed-off URI returns its successor.
- `dive-sites/site-records`: the same for dive sites.

## Impact

- **Lexicons**: new `app.activitytracker.importer.getHandoffs` query; `successor` handling in the segment and dive-site views; mirror `com.atproto.space.deleteSpace` in `core/lexicon` (`Space.kt`).
- **Client**: `core/atproto` (`SpaceClient.deleteSpace`, `OAuthClientConfig.DEFAULT_SCOPE` and the hosted client metadata); `core/data` (a leave job built on the outbox's `AccountPacing`, `describeRepo`/`listRecords`/`applyWrites`, `AthleteSpaces` no longer recreating a space this device has seen, an account-left check before draining, local data cleared per account, the segment star rewrite); `feature/settings` (Leave screen, progress, the other-device prompt); `:app-fixtures`, `:ui-resources`, screenshot references.
- **AppView**: a `segment_handoffs` table (Flyway migration) filled by the segment and dive-site delete paths, including `deleteActor`; successor linking when the importer's copy is indexed or a live duplicate takes over; name history carried over; `getSegment`/`getDiveSite` resolving old URIs; the `getHandoffs` route, open only to the importer.
- **Segment importer**: a `handoff` command that publishes pending segment and site handoffs.
- **Builds on** `data-export` (archived): the leave screen uses the Account page's export, and the repo deletion walks its `AppCollections` list. **Depends on** the pinned 2026-11-05 Spaces alpha PDS for `deleteSpace` and the `manage=delete` scope.
