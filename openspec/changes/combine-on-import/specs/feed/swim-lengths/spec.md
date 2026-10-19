# Spec Delta

## REMOVED Requirements

### Requirement: Merging fills missing lengths
**Reason**: Merge is replaced by combining (`feed/import-duplicates`, `feed/combined-activities`): every recording is kept as a member, and the combined activity takes this data from the highest-ranked member that has it, so nothing needs copying into another recording.
**Migration**: Choose Combine on the import screen, or "Add a recording…" on the activity page. Activities merged before this change keep the data merged into them.
