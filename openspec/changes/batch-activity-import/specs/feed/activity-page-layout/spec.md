# Spec Delta

## MODIFIED Requirements

### Requirement: Import notices above the preview
The import screen's duplicate notice and its time zone choice for a file without one SHALL show above the preview, since they change what it shows. An import of several activities, from a logbook or from several files or an archive, SHALL show its batch form in place of the preview.

#### Scenario: Zone-less dive log
- **WHEN** the athlete picks a dive log whose times have no zone
- **THEN** the time zone choice shows above the dive's hero

#### Scenario: Logbook
- **WHEN** the athlete picks a logbook of several dives
- **THEN** they see the batch form, not a single activity's preview

#### Scenario: Several files
- **WHEN** the athlete picks two FIT files at once
- **THEN** they see the batch form, not a single activity's preview
