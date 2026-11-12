# Proposal

## Why

The activity forms are the last everyday screens on iOS still drawn by the shared Compose UI inside the native shell: editing an
activity (opened from the native activity page), logging one by hand (from the feed's Add), and creating a dive site from a dive (from
the activity page's menu). Their pieces (the details form, the gear picker, the visibility sections, the type picker) are the ones the
native gear editors, Activity defaults and the feed's type sheet already built, so the forms can be native now.

## What Changes

- **Edit activity is native on iOS** (`EditActivityView`), a grouped `Form`: the title (its placeholder the activity's type), a dive's
  site, the description, the photos (each removable, Add photos while there is room, picked with the system's photo picker and
  compressed as the shared form compresses them), the gear, who sees the activity and each category (the native visibility sections),
  and Leave out of leaderboards when offered. Cancel leads the bar and Save trails it; a failed save shows at the top. Cancel with
  unsaved changes asks before discarding, and Save asks first when it would narrow a Public activity or widen its core charts, as the
  shared form does. Loading follows `ui/content-loading`.
- **Manual entry is native on iOS** (`ManualActivityView`): the type (a row opening a searchable type sheet with the quick types, and
  the chosen sport's sub-sports as choices), the title, the units (segmented), distance, duration and temperature with their units in
  their labels, the gear, the visibility sections, the failure, and Save in the bar, held back until a duration is entered.
- **Create dive site is native on iOS** (`CreateDiveSiteView`): the site's outline on the native route map, the radius with a slider,
  the name, the public note, any duplicates to open, the allowance reached, the failure, and Publish in the bar with progress; or why no
  site can be made; loading follows `ui/content-loading`.
- **Kotlin chooses what the forms say** (dev/shared-display-text): the gear field's summary, an option's line, a collection's line and
  the left-out count (`GearFieldTexts`, `:app-common-api`); the type picker's groups for a query and the sub-sport choices with their
  labels (`SportTypeGroups`); manual entry's unit labels (`ManualActivityTexts`); Edit's core-charts warning (`EditActivityTexts`); the
  dive site form's radius, unavailable reasons and public note (`CreateDiveSiteTexts`) and its map (`CreateDiveSiteState.routeMap`).
  The Compose forms resolve the same texts, with no visible change.
- **Manual entry publishes when its duration becomes valid**: the state carries whether the duration is entered as a field the
  presenter fills from the text, so a native Save follows typing (as `ReportState.noteWritten` does).
- **Recorded, not rebuilt:** `ProposePlaceNameScreen` and `FeedFilterScreen` are sub-screens, already native (`ProposePlaceNameSheet`,
  `FeedFilterBar`) and placed on every page that shows them (the dive site and segment pages, the feed).

Out of scope: Android, desktop and web beyond the shared rules moving; the import, record and create-segment screens; editing a
combination; anything on the activity page (another session's).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `feed/activity-visibility`: editing an activity, and entering one by hand, are native on iOS with the same choices and warnings.
- `record/activity-type-picker`: the native type picker for manual entry.
- `gear/activity-gear`: the native gear picker on the activity forms.
- `dive-sites/site-creation`: creating a dive site is native on iOS.
- `dev/shared-display-text`: the activity forms' and the dive site form's texts are chosen in shared code.

## Impact

- **`:app-common-api`:** `GearFieldTexts`, `SportTypeGroups` additions; tests.
- **`:app-common-ui`:** `GearPicker` and the type sheet ask them.
- **`:feature-record-api`, `-presenter`, `-ui`:** `ManualActivityTexts`, the duration field on `ManualActivityState`; the Compose
  form and `ActivityTypePicker` ask them.
- **`:feature-edit-api`, `-ui`:** `EditActivityTexts`.
- **`:feature-divesite-api`, `-ui`:** `CreateDiveSiteTexts`, `CreateDiveSiteState.routeMap`.
- **`:app-fixtures`:** variants for the three forms.
- **`iosApp`:** `EditActivityView`, `ManualActivityView`, `CreateDiveSiteView`, shared form pieces (`ActivityDetailsSections`,
  `GearFieldRow`, `ActivityTypeRow`), aliases, samples, previews, snapshots, registry entries and their test.
- **Docs:** the roadmap's phases, findings and open items; `.agents/rules/compose-screens.md`.
- **Risk carried:** the activity-page session edits the same shared Swift files; entries are added as separate lines.
