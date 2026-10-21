# Proposal

## Why

Segments need efforts matched against exact positions, and the AppView has never seen them. Today an activity's full track (`feed.activityTrack`) lives only in the athlete's `self` space for them alone, and the AppView gets only the simplified polyline, already trimmed by privacy zones. This change is the first in the segments roadmap. With the athlete's explicit consent, it gives the AppView a full-resolution track it can match on, with the privacy zones cut out, so later changes can build segments, efforts and leaderboards without the AppView ever learning where an athlete lives.

## What Changes

- **Segment matching setting.** A new `actor.segmentPrefs` record (rkey `self`) in the athlete's `self` space holds whether segment matching is on.
  - It is **off until the athlete turns it on**. The app asks once, and Settings has a toggle.
  - Saving it is a direct call that reports a failure, as saving zones is.
- **Segment track.** A new `feed.segmentTrack` record, under the activity's rkey, in the athlete's `self` space.
  - It holds every point of the activity's track except those inside the athlete's privacy zones or within the hide-start/end distance.
  - Where the track passes through a zone it is split into separate pieces, never joined across the zone.
  - Times are offsets from the activity's start, never wall-clock times.
  - It is written only while matching is on, and never untrimmed: if the zones can't be read, it waits.
- **Lifecycle follows the full track.** Publishing, combining and deleting an activity write or delete its segment track along with its `feed.activityTrack`.
  - Turning matching on writes segment tracks for existing activities that have a track.
  - Turning it off deletes them all.
  - Both run as resumable sweeps that finish in a later session after an interruption.
- **Zone changes re-trim segment tracks** the same way they re-trim polylines, and resume after an interruption the same way.
- **The AppView indexes both records from `self` only.**
  - It never serves them.
  - It never uses a segment track while the athlete's matching is off.
  - It drops all of an athlete's segment tracks when matching goes off, the `self` space is deleted, or the account goes away.
  - It still ignores `feed.activityTrack` and every other record in `self`.
  - Matching itself comes in the next change (`segments-core`). This change only makes the data available, with consent.
- `space/athlete.json` declares the two new collections, so athletes sign in again to get the scope. Without it, the toggle is unavailable.

## Capabilities

### New Capabilities
- `segments/segment-track`: The athlete's consent to segment matching, and the privacy-zone-trimmed copy of each activity's track that the AppView may match against: where it is stored, what it holds, when it's written, re-trimmed and deleted, and what the AppView may do with it.

### Modified Capabilities
- `profile/privacy-zones`: Re-trimming after a zone change, finishing an interrupted re-trim, and the rule that unreadable zones never widen a route now cover segment tracks as well as polylines.
- `spaces/appview-syncer`: The set of records the AppView indexes from spaces gains `feed.segmentTrack` and `actor.segmentPrefs`, from the `self` space only. It also lists `feed.activityDetails`, which the AppView already indexes but the spec left out.

## Impact

- **Lexicons:** new `lexicons/app/activitytracker/feed/segmentTrack.json` and `actor/segmentPrefs.json`, plus the `space/athlete.json` collections; the Kotlin mirror in `core/lexicon` (`Records.kt`, `Nsid.kt`).
- **Client (`core/data`, `core/location`):**
  - `TrackMath.trim` gets a variant that splits instead of joining.
  - New segment-track store and sweep, alongside `ActivityTrackStore` and `RouteTrimmer`.
  - Hooks where `ActivityRepository` and `ActivityManager` put and delete tracks.
  - `PrivacyZones` gates writes on known zones.
- **App:** a consent prompt, plus a toggle on `SettingsScreen` / `SettingsPresenter` / `SettingsUi`.
- **AppView:**
  - `Indexer.spaceCollections` (self-specific set), new tables and a `V27__` migration, delete and purge paths (`delete`, `dropSpace`, `deleteActor`).
  - No new routes.
- **Rules and docs:** `.agents/rules/appview.md` and the CLAUDE.md data-flow paragraph change. The AppView now holds a consented, zone-trimmed track copy, and that has to be stated plainly. The "AppView as a consented member" BACKLOG item gets updated.
- **Privacy:** the AppView database now references private track data for consenting athletes. Backups must be treated accordingly, as they already are.
