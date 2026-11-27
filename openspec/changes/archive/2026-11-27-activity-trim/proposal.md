# Proposal

## Why

Athletes start the recorder before they set off (standing in a car park) and stop it after they're done (a café, the drive home). Watch files carry the same junk at either end. That junk inflates elapsed time, adds driving distance and speed, pollutes segment efforts and grouping, and today the only fix is to delete the activity. Trimming the start or end is the most common edit the category offers, and we have none.

## What Changes

- **Trim an activity.** On their own activity, an athlete can cut the start and/or end. They pick the kept range on a profile with two handles, by **time** (always) or by **distance** (when the activity has distance), and a map previews the cut when it has GPS. Start and end readouts show the time in, the clock time and the distance in; tapping one opens an exact value with steppers. Totals show before → after. This follows option A of the mockups (`.impeccable/mockups/activity-trim.html`).
- **Destructive, recomputed.** Saving drops every point, sample, lap and length outside the kept range. It moves the start time to the new first point and recomputes distance, moving and elapsed time, elevation, speeds, heart rate and power figures, zones, laps and the route. A confirmation says the cut data can't be brought back. Segment efforts and group pairs are found again from the trimmed track.
- **Suggested trim.** From the recording, the app detects standing time before the athlete set off, and a stop followed by standing or travel too fast for the sport at the end. It offers the cut as one tap, following option C of the mockups.
- **Where it's offered:**
  - the owner's ⋯ menu on the activity page, as "Trim start or end";
  - the recording screen's finish form, which carries the suggestion;
  - the import preview, per activity, which also carries the suggestion and previews it on the map.

  The recording and import entry points trim the draft before anything is published.
- **Manual entries trim by time only.** Trimming moves the start and end times, the distance toggle is disabled with a reason, and the entered distance is kept.
- **Not offered** for combined activities, members of a combined activity, multisport activities, dives and strength workouts with sets. The menu item is absent for these.
- **The original file stays as imported.** Trimming doesn't change an imported activity's source file, which can still be downloaded whole.
- **Not in this change:** undoing a saved trim, cutting out the middle of an activity, splitting an activity in two, and a trim on watch or bike-computer devices.

## Capabilities

### New Capabilities
- `feed/activity-trim`: trimming an owned activity's start and end by time or distance:
  - the editor and its map and profile preview;
  - the recompute and the rewrite under the same record key;
  - the suggestion;
  - the entry points;
  - which activities can't be trimmed.

### Modified Capabilities
- `feed/activity-source-file`: trimming an activity leaves its source file unchanged and downloadable.

## Impact

- **core/data:** a pure trimmer, which takes a stored or drafted activity and a kept span and rebuilds the record summary, streams (re-based to the new start), route, GPX and laps. A suggestion detector. An `ActivityManager.trim` that rewrites the activity, details, streams, track and segment track under the same rkey through the outbox, reusing the rebuild path that combine uses.
- **feature:** a new `feature/trim` (api, presenter, ui) for the editor screen. `feature/detail` gains the menu item and its event. `feature/record` gains the trim row in the recording finish form and the import preview.
- **iOS:** a native trim screen, a menu item in `ActivityPageMenu.swift`, and rows in the record and import views.
- **AppView:** no new code. A rewritten segment track already queues a rematch, and grouping rechecks overlap.
- **No lexicon changes.**
- **Fixtures and tests:** sample states in `:app-fixtures`, trimmer and suggestion unit tests, presenter tests, screenshot and snapshot references.
