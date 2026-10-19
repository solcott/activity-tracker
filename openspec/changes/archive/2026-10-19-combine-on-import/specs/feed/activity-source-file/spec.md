# Spec Delta

## MODIFIED Requirements

### Requirement: Grouped recordings keep their own source
An imported activity combined with a logged activity SHALL keep its own source as a member, and a combined activity SHALL have no source of its own. Splitting a combined activity, removing a member, and editing an activity's title, description, dive site or photos, SHALL leave each source as it is.

#### Scenario: Combine a duplicate
- **WHEN** an athlete imports a FIT dive and combines it with a logged dive
- **THEN** the imported member has the FIT file as its source, and the logged dive's source is unchanged

#### Scenario: Split
- **WHEN** an athlete splits a combined activity into its members
- **THEN** each member keeps its source

#### Scenario: Group a duplicate
- **WHEN** an athlete's legacy group holds a FIT dive as another recording of a logged dive
- **THEN** the recording has the FIT file as its source, and the logged dive's source is unchanged

#### Scenario: Ungroup
- **WHEN** an athlete splits a recording off a legacy group's main activity
- **THEN** the recording and the main activity keep their sources

## REMOVED Requirements

### Requirement: Merging fills a missing source
**Reason**: Merge is replaced by combining (`feed/import-duplicates`, `feed/combined-activities`): every recording is kept as a member, and the combined activity takes this data from the highest-ranked member that has it, so nothing needs copying into another recording.
**Migration**: Choose Combine on the import screen, or "Add a recording…" on the activity page. Activities merged before this change keep the data merged into them.
