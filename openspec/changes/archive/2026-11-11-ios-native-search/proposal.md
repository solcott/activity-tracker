# Proposal

## Why

Search is the second tab's root and, after the feed, the last tab root on iOS still drawn by the shared Compose UI: its bar, its
segmented choice, its search field and its rows look and move unlike the native pages around them. `LeftElsewhereScreen`, the choice
shown in place of the app after leaving on another device, is the last of the account screens still shared (the roadmap's open items).
Both are small lists whose words still follow rules chosen inside composables (the title by mode, the prompt, "No athletes match", the
pending-work lines and their count), which a native page can't ask.

## What Changes

- **The Search tab's root is native on iOS** (`AthleteSearchView`), registered in `ScreenRegistry`: the large title of a tab root
  ("Find athletes", or "Search" in Segments mode), Athletes and Segments as a segmented choice under the bar, and in Athletes mode the
  system's search field (`searchable`) over an inset grouped list of accounts (picture, name and handle, opening the profile) loaded page
  by page with `PagedList`, the prompt while the query is blank, "No athletes match …" when nothing matched, and the search's progress
  and failure with Retry as `ui/content-loading` says. The query, the mode and the results stay the presenter's, so they are kept across
  tabs as today.
- **Segments mode keeps the shared segment browse screen**, embedded under the native choice: a Compose view controller that runs
  `SegmentBrowseScreen` as the shared Search tab does, opening what it opens on the Search tab's stack. Segment browse, with its map,
  goes native with the native maps, later.
- **`LeftElsewhereScreen` is native on iOS** (`LeftElsewhereView`): an inset grouped list under its title, saying what this device still
  holds (the changes counted, an import saving, an export not saved, a recording), the choice, a failure, and Rejoin and Discard and sign
  out, off while one runs; progress while it is still checking. It is shown alone in place of the app, as now, in a stack of its own so
  its title shows.
- **Kotlin chooses what both screens say, both UIs ask** (`AthleteSearchTexts` in `:feature-search-api`, `LeftElsewhereTexts` in
  `:feature-settings-api`): the title and mode names, the prompt and the empty message, the intro and the pending-work lines. The
  changes count is grouped by thousands as `ui/number-format` asks, where the Compose screen wrote "1200"; nothing else changes on
  Android, desktop or web.
- **Samples, previews and snapshots** for both screens from `:app-fixtures`, light and dark, recorded on iOS 26.0; the registry
  entries and their test.

What waits (open items in the roadmap):

- **Native segment browse**, with the native map; until then Segments mode is the shared browse inside the native page.
- **The leave in progress** (`LeaveView` shown alone while a leave resumes) still has no stack of its own, so its title doesn't show.

Out of scope: segment browse, the profile it opens, and the Search tab's role in the tab bar (iOS 26's search tab).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `segments/segment-browse`: the Search tab, with its Athletes and Segments switch, is native on iOS, Segments still showing the shared
  browse screen.
- `auth/leaving`: the choice after leaving on another device is native on iOS.
- `dev/shared-display-text`: the Search tab's title, modes, prompt and empty message, and what the left-elsewhere choice says, are
  chosen in shared code.

## Impact

- **`:feature-search-api`:** `AthleteSearchTexts`; tests. **`:feature-search-ui`:** `AthleteSearchUi` asks it.
- **`:feature-settings-api`:** `LeftElsewhereTexts`; tests. **`:feature-settings-ui`:** `LeftElsewhereUi` asks it.
- **`:composeApp` (iosMain) and `:iosShell`:** `SegmentBrowseViewController(record)`, the shared browse embedded for the native Search
  page, exported as `IosApp.segmentBrowseViewController`.
- **`:app-fixtures`:** sample variants (Segments mode, a failed search, a next page loading) where missing.
- **`iosApp`:** `AthleteSearchView`, `LeftElsewhereView`, `SegmentBrowseEmbed`, type aliases, samples, previews, snapshot and text tests,
  the registry entries, `SessionView`'s stack around the left-elsewhere screen.
- **Docs:** the roadmap's phase 4 entry, findings and open items; `.agents/rules/compose-screens.md`.
- **Risk carried:** a Compose view controller inside a native page (keyboard, safe area, the map's gestures) is new; it is walked on the
  iPhone. `LeftElsewhereScreen` needs another device to leave, so on a device it is seen only if the local stack can stage it.
