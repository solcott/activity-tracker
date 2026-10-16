# Spec Delta

## ADDED Requirements

### Requirement: Zones record carries training load
An activity's training load (FTP, intensity factor, training stress score, aerobic and anaerobic training effect) SHALL be stored on its `feed.activityZones` record, in the same place as its zone snapshots. An activity with training load and no zone snapshots SHALL still have a zones record, holding only the training load.

#### Scenario: Ride with zones and TSS
- **WHEN** an athlete publishes an imported ride with zone snapshots and a TSS
- **THEN** its zones record holds both

#### Scenario: Training load without zones
- **WHEN** an athlete with no thresholds set publishes an imported ride whose file has an FTP and TSS
- **THEN** a zones record with only the training load is stored at the narrower of the ride's charts visibility and the athlete's training visibility

### Requirement: Training load moves with the zones
Training load SHALL move, narrow and widen exactly as the zones record it is on does.

#### Scenario: Training narrowed
- **WHEN** an athlete narrows training from Public to Only me
- **THEN** each activity's training load moves to their `self` space with its zones
