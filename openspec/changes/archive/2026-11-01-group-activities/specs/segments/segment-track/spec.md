# Spec Delta

## MODIFIED Requirements

### Requirement: Segment matching is off until the athlete turns it on
Whether segment matching is on SHALL be one `actor.segmentPrefs` record in the athlete's `self` space. It SHALL NOT be in the repo or any other space. An athlete with no such record, or one that doesn't say matching is on, SHALL be treated as having matching off.

#### Scenario: New athlete
- **WHEN** an athlete who has never chosen segment matching or grouping publishes a GPS activity
- **THEN** no segment track is written for it

#### Scenario: Setting stored privately
- **WHEN** an athlete turns segment matching on
- **THEN** an `actor.segmentPrefs` record saying so is in their `self` space, and their repo holds no record of the choice

### Requirement: Segment tracks follow the full track while matching is on
While segment matching is on or grouping (`feed/group-activities`) isn't Off, every activity that has a full track SHALL get a segment track, queued with its activity and sent with it offline or online. When an activity's full track is replaced, as when activities are combined, its segment track SHALL be replaced too. Deleting an activity's full track SHALL delete its segment track.

#### Scenario: Publish offline
- **WHEN** an athlete with matching on publishes a GPS activity offline, then reconnects
- **THEN** the activity, its `feed.activityTrack` and its `feed.segmentTrack` are all written

#### Scenario: Grouping on, matching off
- **WHEN** an athlete with segment matching off who chose Connected grouping publishes a GPS activity
- **THEN** its `feed.segmentTrack` is written to their `self` space

#### Scenario: Delete
- **WHEN** an athlete deletes an activity that has a segment track
- **THEN** its `feed.segmentTrack` is gone from their `self` space

#### Scenario: Visibility change
- **WHEN** an athlete changes an activity's visibility
- **THEN** its segment track stays in their `self` space, unchanged

#### Scenario: Manual entry
- **WHEN** an athlete with matching on publishes a manual entry without positions
- **THEN** no segment track is written for it

### Requirement: Turning matching on covers earlier activities
Turning segment matching on, or choosing a grouping level other than Off, while neither was on before SHALL write a segment track for each of the athlete's existing activities that has a full track. Work that doesn't finish because the app closed, went offline or failed SHALL finish in a later session on any of the athlete's devices.

#### Scenario: Existing activities
- **WHEN** an athlete with 50 GPS activities turns matching on
- **THEN** each of the 50 gets a segment track

#### Scenario: Grouping turned on first
- **WHEN** an athlete with matching off and 50 GPS activities chooses Anyone grouping
- **THEN** each of the 50 gets a segment track

#### Scenario: Interrupted
- **WHEN** the app closes after 20 of them are written
- **THEN** a later session writes the other 30 without the athlete turning matching on again

### Requirement: Turning matching off removes every segment track
Turning segment matching off, or choosing grouping Off, SHALL save the setting first. When after that save segment matching is off and grouping is Off, every `feed.segmentTrack` in the athlete's `self` space SHALL then be deleted. Deletes that don't finish SHALL finish in a later session on any of the athlete's devices, and no segment track SHALL be written while both are off. While either is on, turning the other off SHALL NOT delete segment tracks.

#### Scenario: Turn off
- **WHEN** an athlete whose grouping is Off turns matching off
- **THEN** their setting says off, and every segment track in their `self` space is deleted

#### Scenario: Grouping still on
- **WHEN** an athlete who chose Anyone grouping turns segment matching off
- **THEN** their segment tracks stay in their `self` space

#### Scenario: Interrupted delete
- **WHEN** the app closes before every segment track is deleted
- **THEN** a later session deletes the rest

### Requirement: The AppView keeps segment tracks only while matching is on
The AppView SHALL use segment track data only for purposes turned on: efforts and candidate riders while segment matching is on, pairs while grouping isn't Off, nothing while both are off. When a purpose goes off, it SHALL drop what it derived for that purpose. When both are off, or the `self` space or account is deleted, it SHALL drop everything it holds from their tracks.

#### Scenario: Matching turned off
- **WHEN** an athlete whose grouping is Off turns matching off
- **THEN** once the AppView indexes the new setting, it holds nothing from their segment tracks, even before the deletes reach it

#### Scenario: Matching off, grouping on
- **WHEN** an athlete who chose Anyone grouping turns matching off
- **THEN** once the AppView indexes the new setting, it holds and serves none of their efforts and no rider records, and it still pairs their activities

#### Scenario: Grouping off, matching on
- **WHEN** an athlete with matching on chooses grouping Off
- **THEN** once the AppView indexes the choice, it holds none of their pairs, and their efforts stay

#### Scenario: Stray track
- **WHEN** an athlete's `self` space holds a segment track while their settings say matching is off and grouping is Off
- **THEN** the AppView doesn't read or use it

#### Scenario: Efforts dropped with the tracks
- **WHEN** an athlete with efforts on several segments turns matching off
- **THEN** once the AppView indexes the new setting, it holds and serves none of those efforts

#### Scenario: Candidate riders dropped with the tracks
- **WHEN** an athlete counted as a rider of several staged candidates turns matching off
- **THEN** once the AppView indexes the new setting, it holds no record that they rode any of them

### Requirement: The AppView never serves segment tracks
The AppView SHALL NOT serve a segment track, its points, its leaderboard or group exclusion, the matching setting or the grouping choice to anyone, the athlete included. Only the athlete and the AppView SHALL be able to read them.

#### Scenario: Another viewer
- **WHEN** a mutual of the athlete opens one of the athlete's Public activities with a segment track
- **THEN** the views they get hold no points from the segment track and no link to it

#### Scenario: Grouping choice
- **WHEN** any viewer, the athlete included, opens the athlete's profile or activities
- **THEN** nothing served says which grouping choice the athlete made

### Requirement: A segment track keeps its leaderboard exclusion
A segment track SHALL say whether its activity is excluded from leaderboards, not excluded when absent. Rewriting a segment track for the same activity, as when it is trimmed again by changed zones, SHALL keep the mark. A segment track written for a new activity, or for a rebuilt combined activity, SHALL start unexcluded. Turning matching and grouping both off deletes the segment tracks, so it SHALL forget every mark.

#### Scenario: Re-trimmed by new zones
- **WHEN** an athlete excludes a ride, then adds a privacy zone, and the ride's segment track is rewritten
- **THEN** the new segment track is still excluded

#### Scenario: Matching off and on
- **WHEN** an athlete whose grouping is Off excludes a ride, turns matching off and later on again
- **THEN** the ride's new segment track isn't excluded

#### Scenario: Matching off while grouping on
- **WHEN** an athlete who chose Anyone grouping excludes a ride, turns matching off and later on again
- **THEN** the ride's segment track is still excluded

## ADDED Requirements

### Requirement: A segment track keeps its group exclusion
A segment track SHALL say whether its activity left its group (`feed/group-activities`), not left when absent. Leaving or rejoining SHALL rewrite the track with the mark changed and its leaderboard exclusion kept. Rewriting a track for the same activity SHALL keep the mark. A track for a new or rebuilt combined activity SHALL start not left. Deleting the tracks SHALL forget every mark.

#### Scenario: Re-trimmed by new zones
- **WHEN** an athlete leaves the group on a ride, then adds a privacy zone, and the ride's segment track is rewritten
- **THEN** the new segment track still says the ride left its group

#### Scenario: Both marks kept
- **WHEN** an athlete who excluded a ride from leaderboards leaves the group on it
- **THEN** its segment track says both that it is excluded from leaderboards and that it left its group

#### Scenario: Connection lost before the leave is sent
- **WHEN** an athlete leaves the group on a ride and the connection drops before the rewritten segment track is sent
- **THEN** it is sent once they reconnect, and the ride is unpaired once the AppView indexes it
