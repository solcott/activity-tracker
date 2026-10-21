# Spec Delta

## MODIFIED Requirements

### Requirement: Changing zones re-trims existing activities
Adding, moving or removing a zone, or changing the hide-start/end distance, SHALL rewrite the polyline of each of the athlete's activities that has a full track, at every visibility, from that track by the new settings. While segment matching is on, it SHALL also rewrite each of those activities' segment tracks from the full track by the new settings. Activities without a track SHALL be left as they are.

#### Scenario: Add a zone
- **WHEN** an athlete adds a zone around a place where earlier Public and Only me activities started
- **THEN** each of those activities' records gets a polyline trimmed by it

#### Scenario: Remove a zone
- **WHEN** an athlete removes a zone
- **THEN** the parts of their activities' routes it hid come back in their polylines, from each activity's track

#### Scenario: Manual entry
- **WHEN** an athlete changes their zones and has a manual entry with no track
- **THEN** that entry's record is not rewritten

#### Scenario: Nothing else changes
- **WHEN** an activity is re-trimmed
- **THEN** its title, description, photos, visibility, totals and rkey are unchanged

#### Scenario: Segment tracks re-trimmed
- **WHEN** an athlete with segment matching on adds a zone around a place their earlier activities pass through
- **THEN** each of those activities' segment tracks is rewritten without the points inside the new zone

### Requirement: Re-trimming finishes after an interruption
A re-trim that doesn't finish, because the app was closed, went offline or failed, SHALL finish in a later session on any of the athlete's devices, without the athlete saving their zones again. This SHALL hold for polylines and segment tracks alike.

#### Scenario: App closed mid re-trim
- **WHEN** an athlete adds a zone and closes the app before every activity is rewritten
- **THEN** the next session rewrites the rest

#### Scenario: Re-trim done
- **WHEN** a re-trim has rewritten every activity
- **THEN** later sessions don't rewrite them again until the zones change

#### Scenario: Segment tracks left behind
- **WHEN** an athlete with segment matching on adds a zone and the app closes before every segment track is rewritten
- **THEN** a later session rewrites the remaining segment tracks by the new zones

### Requirement: Zones that can't be read never widen a route
When a session can't read the athlete's zones, publishing SHALL trim by the zones last read this session. If none were read, the activity SHALL be written with no polyline, never an untrimmed one, and SHALL get a trimmed one once the zones are read. No segment track SHALL be written until the zones are read in this session. A session without the `space:` scope has no zones and trims nothing.

#### Scenario: Offline after reading zones
- **WHEN** an athlete reads their zones, goes offline and records an activity
- **THEN** its polyline is trimmed by those zones

#### Scenario: Offline before reading zones
- **WHEN** a session that hasn't read the athlete's zones yet publishes a GPS activity offline
- **THEN** its record is written with no polyline, and once the session reads the zones the record is rewritten with a trimmed one

#### Scenario: No zones record
- **WHEN** the athlete's `self` space has no `actor.privacyZones` record
- **THEN** the zones read as none, and activities are published with untrimmed polylines

#### Scenario: Segment track waits for zones
- **WHEN** a session with segment matching on that hasn't read the athlete's zones publishes a GPS activity
- **THEN** no segment track is written for it until the zones are read
