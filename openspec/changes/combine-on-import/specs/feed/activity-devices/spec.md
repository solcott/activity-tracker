# Spec Delta

## MODIFIED Requirements

### Requirement: Grouped recordings keep their own devices
An imported activity combined with a logged activity SHALL keep its own devices as a member, and a combined activity SHALL have no devices record of its own. Splitting a combined activity, removing a member, and editing an activity SHALL leave each activity's devices as they are.

#### Scenario: Two recordings of one ride
- **WHEN** an athlete combines an Edge 840 recording of a ride with a Fenix 7 recording of the same ride
- **THEN** each member keeps its own devices, naming its own recorder

## REMOVED Requirements

### Requirement: Merging fills missing devices
**Reason**: Merge is replaced by combining (`feed/import-duplicates`, `feed/combined-activities`): every recording is kept as a member, and the combined activity takes this data from the highest-ranked member that has it, so nothing needs copying into another recording.
**Migration**: Choose Combine on the import screen, or "Add a recording…" on the activity page. Activities merged before this change keep the data merged into them.
