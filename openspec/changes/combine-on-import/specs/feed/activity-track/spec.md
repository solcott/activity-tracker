# Spec Delta

## MODIFIED Requirements

### Requirement: Edits and ungroups keep the track
Editing an activity's title, description, dive site or photos, combining it with other activities, splitting a combined activity, and removing a member SHALL leave each member's full track as it is.

#### Scenario: Edit title
- **WHEN** an athlete changes a GPS activity's title
- **THEN** its `feed.activityTrack` is unchanged

#### Scenario: Split a combined activity
- **WHEN** an athlete splits a combined activity into its members
- **THEN** each member's `feed.activityTrack` is unchanged

#### Scenario: Ungroup a recording
- **WHEN** an athlete splits a recording off a legacy group's main activity
- **THEN** the recording's `feed.activityTrack` is unchanged

## REMOVED Requirements

### Requirement: Merging fills a missing track
**Reason**: Merge is replaced by combining (`feed/import-duplicates`, `feed/combined-activities`): every recording is kept as a member, and the combined activity takes this data from the highest-ranked member that has it, so nothing needs copying into another recording.
**Migration**: Choose Combine on the import screen, or "Add a recording…" on the activity page. Activities merged before this change keep the data merged into them.
