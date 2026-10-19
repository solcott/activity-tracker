# Proposal

## Why

Imports still offer two duplicate actions, and only for dives:
- **Merge** fills in what the logged activity lacks and doesn't keep the import as its own recording.
- **Group** (`recordingOf`) links the import as another recording but leaves the logged activity's figures and charts as they were.

Runs and rides imported from a second device get no duplicate check at all (`BACKLOG.md`, "Duplicates beyond dives"). With combined activities in place (`combine-activities`), both actions can become one: keep every recording and combine them. That gives one concept for athletes, and one write path to maintain.

## What Changes

**Duplicate check at import**
- The check covers every sport. An import is a likely duplicate of a logged activity, or of an earlier one in the same file, when their times overlap by at least half the shorter one.
- The zone-shift match (same length, a whole number of hours off) stays for files without a UTC offset.
- The duplicate notice offers **Ignore**, **Import anyway** and **Combine**:
  - Combine imports the file as its own activity and combines it with the logged one, or adds it to the logged one's combined activity.
  - Within a logbook, Combine joins dives the file holds twice.

**Removed**
- **BREAKING:** the Merge action is gone, along with the requirements that described what merging fills in, across:
  - activity data, track, source file, devices, swim lengths, multisport legs, strength sets and dive data.

  Combining keeps all of that data in the members and builds the combined activity from the best of it.
- **BREAKING:** the Group action is gone. New imports never write `recordingOf`.

**Adding a recording from the activity page**
- "Add a recording…" on the athlete's own activity page picks a file, previews it, and combines it with that activity.
- A file starting more than 12 hours from the activity is refused, with the reason given.

**Existing groups**
- The athlete's client turns each existing `recordingOf` group into a combined activity once, after sign-in:
  - the primary and its recordings become members;
  - each recording's `recordingOf` is cleared;
  - activities merged in the past stay as they are.
- `recordingOf` stays in the lexicon, marked legacy. The AppView keeps honouring it for groups not yet migrated, until a later change removes it.

## Capabilities

### New Capabilities
- `feed/import-duplicates`: finding the logged activities an import (or a logbook entry) repeats, for every sport, and what the athlete can do about each one: ignore, import anyway, or combine. Also adding a recording to an activity from its page.

### Modified Capabilities
- `feed/activity-file-data`: merging requirements removed.
- `feed/activity-track`:
  - merging fills a missing track: removed;
  - edits and ungroups keep the track: now about combining and splitting.
- `feed/activity-source-file`:
  - merging fills a missing source: removed;
  - grouped recordings keep their source: now about members.
- `feed/activity-devices`:
  - merging fills missing devices: removed;
  - grouped recordings keep their devices: now about members.
- `feed/swim-lengths`, `feed/multisport-legs`, `feed/strength-sets`, `feed/dive-gas-switches`, `feed/dive-data`: merging requirements removed.
- `feed/activity-visibility`: recordings sharing their primary's visibility now applies only to legacy groups, and clients no longer create them.

## Impact

- **core/data:**
  - `DiveMatcher` becomes `ActivityMatcher`, for every sport;
  - `ActivityRepository.mergeInto` and `ActivityMerge.fillGaps`'s merge-only uses are removed (the helpers the combiner uses stay);
  - `ActivityManager.combine` accepts a draft as the new member;
  - a one-time `LegacyGroupMigration`.
- **Screens:**
  - `ImportActivityPresenter`, `OwnDives`, `DiveBatches`, `DuplicateNotice` and `ImportPreviewUi` drop Merge and Group (`DuplicateAction`, `MergeGain`) and gain Combine;
  - the activity page gains "Add a recording…", which opens the import screen with a target activity.
- **Lexicons:** `feed/activity.json`'s `recordingOf` description is marked legacy. No structural change.
- **AppView:** unchanged. It keeps reading `recordingOf`.
- **Specs:** ten existing capabilities lose or change requirements (listed above).
