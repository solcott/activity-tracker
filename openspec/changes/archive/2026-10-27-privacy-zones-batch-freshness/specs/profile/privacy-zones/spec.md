# Spec Delta

## ADDED Requirements

### Requirement: A batch's read of the zones applies only to its own publishes
When an import publishes many activities with one read of the zones, only the import's own activities SHALL be trimmed by that read. Any other publish made while the import runs SHALL read the zones for itself, as a publish outside an import does.

#### Scenario: Recording saved during an import
- **WHEN** an athlete starts a logbook import, the zones change on another device, and the athlete saves a recording before the import finishes
- **THEN** the recording's polyline is trimmed by the zones read for that publish, not by the import's earlier read

#### Scenario: Offline publish during an import
- **WHEN** an activity is published outside a running import and the zones can't be read for it
- **THEN** it's trimmed by the zones last read this session, or written with no polyline if there are none, and re-trimmed once the zones are read, whether or not the import's read was fresh

### Requirement: A zone change during a batch reaches its remaining publishes
When the athlete saves their zones, or the session reads newer ones, while an import is publishing, the import's later activities SHALL be trimmed by the new zones. An activity whose trim the new zones replaced before it finished publishing SHALL be re-trimmed by them, polyline and segment track alike.

#### Scenario: Zone saved mid-import
- **WHEN** an athlete adds a zone while a logbook import is still publishing
- **THEN** every activity the import publishes after the save has no point of its polyline inside the new zone

#### Scenario: Save lands during a publish
- **WHEN** a save replaces the zones after a publish was given its trim but before that publish finished
- **THEN** that activity is re-trimmed by the saved zones once they're read, and its segment track is swept to them
