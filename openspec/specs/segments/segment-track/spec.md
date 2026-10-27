# segments/segment-track Specification

## Purpose

Lets an athlete consent to segment matching, and defines the privacy-zone-trimmed copy of each activity's track that the AppView may match against: where it is stored, what it holds, when it is written, re-trimmed and deleted, and what the AppView may do with it.

## Requirements

### Requirement: Segment matching is off until the athlete turns it on
Whether segment matching is on SHALL be one `actor.segmentPrefs` record in the athlete's `self` space. It SHALL NOT be in the repo or any other space. An athlete with no such record, or one that doesn't say matching is on, SHALL be treated as having matching off.

#### Scenario: New athlete
- **WHEN** an athlete who has never chosen publishes a GPS activity
- **THEN** no segment track is written for it

#### Scenario: Setting stored privately
- **WHEN** an athlete turns segment matching on
- **THEN** an `actor.segmentPrefs` record saying so is in their `self` space, and their repo holds no record of the choice

### Requirement: The athlete is asked once
The app SHALL ask an athlete who has never chosen whether to turn segment matching on. It SHALL explain that a copy of their routes, with their privacy zones removed, will be shared with the AppView to find segments, and that the efforts found can appear on segment leaderboards to those who may see them. Declining SHALL be stored as matching off, and the app SHALL NOT ask again. Settings SHALL let the athlete change the choice at any time.

#### Scenario: Decline
- **WHEN** an athlete is asked and declines
- **THEN** their `actor.segmentPrefs` says matching is off, and no device asks them again

#### Scenario: Change later
- **WHEN** an athlete who declined turns segment matching on in Settings
- **THEN** their `actor.segmentPrefs` says matching is on

#### Scenario: Leaderboards explained
- **WHEN** an athlete is asked
- **THEN** the prompt says their efforts can appear on leaderboards, and that they can keep off them in Settings

### Requirement: Saving the setting is a direct call
Saving the segment matching setting SHALL write to the PDS at once and report a failure, not queue the save for later. A failed save SHALL leave the earlier setting in force. A session without the `space:` scope for the setting SHALL NOT offer it.

#### Scenario: Offline save
- **WHEN** an athlete turns matching on while offline
- **THEN** the app reports that it couldn't save, and matching stays off

#### Scenario: Old scope
- **WHEN** a session's `space:` scope doesn't include `actor.segmentPrefs` and `feed.segmentTrack`
- **THEN** the setting isn't offered, and the athlete is told to sign in again to use it

### Requirement: A segment track is stored only in the self space
An activity's segment track SHALL be one `feed.segmentTrack` record, with the activity's rkey, in the athlete's `self` space, whatever the activity's visibility. It SHALL NOT be in the repo or any other space.

#### Scenario: Public activity
- **WHEN** an athlete with matching on publishes a Public GPS activity
- **THEN** its `feed.segmentTrack` is in their `self` space and their repo holds no segment track

### Requirement: A segment track leaves out privacy zones
A segment track SHALL hold every point of the activity's full track except those inside any of the athlete's privacy zones or within the hide-start/end distance of either end, cut where the track crosses each boundary. A line between two points that passes through a zone SHALL be cut where it enters and where it leaves. Where the track leaves and later re-enters the shown area, the parts SHALL be separate pieces, never joined across what was left out.

#### Scenario: Starts at home
- **WHEN** an athlete with a zone around home publishes a ride that starts there
- **THEN** its segment track starts where the ride leaves the zone, and holds no point inside it

#### Scenario: Passes through a zone
- **WHEN** a run passes through one of the athlete's zones mid-route
- **THEN** its segment track has one piece before the zone and one after, with no line between them

#### Scenario: Sparse samples across a zone
- **WHEN** a ride recorded with sparse samples has two points in a row outside a zone, and the straight line between them passes through the zone
- **THEN** its segment track ends one piece where that line enters the zone and starts the next where it leaves, with no line between them

#### Scenario: Line passing beside a zone
- **WHEN** the line between two points comes near a zone but stays outside it
- **THEN** the segment track keeps that line whole, in one piece

#### Scenario: Entirely hidden
- **WHEN** every point of an activity is inside a zone or within the hide-start/end distance
- **THEN** no segment track is written for it

### Requirement: A segment track holds no wall-clock time
Each point of a segment track SHALL carry its position and its time as an offset from the first point of the activity's full track, plus its altitude when the track has one. The segment track SHALL NOT hold any date or time of day.

#### Scenario: Hidden start time
- **WHEN** an activity's Start time category is narrower than the activity and it has a segment track
- **THEN** nothing in the segment track reveals the start time

### Requirement: Segment tracks are never untrimmed
A segment track SHALL be written only by zones read in this session, the last read when a fresh read fails, and SHALL record which saved zones it was trimmed by. When a session hasn't read the athlete's zones, it SHALL NOT write segment tracks until it can. A segment track trimmed by zones other than those saved SHALL be rewritten by the saved zones once they're read.

#### Scenario: Offline before reading zones
- **WHEN** a session that hasn't read the athlete's zones publishes a GPS activity offline with matching on
- **THEN** no segment track is queued, and one is written once the session reads the zones

#### Scenario: Offline after reading zones
- **WHEN** a session reads the athlete's zones, goes offline and publishes a GPS activity with matching on
- **THEN** its segment track is trimmed by those zones and sent with the activity

#### Scenario: Zones changed elsewhere
- **WHEN** a segment track was trimmed by zones that another device has since changed
- **THEN** it is rewritten by the saved zones once a session reads them

### Requirement: Segment tracks follow the full track while matching is on
While matching is on, every activity that has a full track SHALL get a segment track, queued with its activity and sent with it offline or online. When an activity's full track is replaced, as when activities are combined, its segment track SHALL be replaced too. Deleting an activity's full track SHALL delete its segment track.

#### Scenario: Publish offline
- **WHEN** an athlete with matching on publishes a GPS activity offline, then reconnects
- **THEN** the activity, its `feed.activityTrack` and its `feed.segmentTrack` are all written

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
Turning matching on SHALL write a segment track for each of the athlete's existing activities that has a full track. Work that doesn't finish because the app closed, went offline or failed SHALL finish in a later session on any of the athlete's devices.

#### Scenario: Existing activities
- **WHEN** an athlete with 50 GPS activities turns matching on
- **THEN** each of the 50 gets a segment track

#### Scenario: Interrupted
- **WHEN** the app closes after 20 of them are written
- **THEN** a later session writes the other 30 without the athlete turning matching on again

### Requirement: Turning matching off removes every segment track
Turning matching off SHALL save the setting first, then delete every `feed.segmentTrack` in the athlete's `self` space. Deletes that don't finish SHALL finish in a later session on any of the athlete's devices, and no segment track SHALL be written while matching is off.

#### Scenario: Turn off
- **WHEN** an athlete turns matching off
- **THEN** their setting says off, and every segment track in their `self` space is deleted

#### Scenario: Interrupted delete
- **WHEN** the app closes before every segment track is deleted
- **THEN** a later session deletes the rest

### Requirement: The AppView keeps segment tracks only while matching is on
The AppView SHALL NOT read or use any segment track data of an athlete whose matching is off, and SHALL use it only to match segment efforts and to count riders on staged candidate segments. When an athlete's matching goes off, their `self` space is deleted, or their account is deleted, the AppView SHALL drop everything it holds from their segment tracks, the efforts matched from them and their rider records on candidates included.

#### Scenario: Matching turned off
- **WHEN** an athlete turns matching off
- **THEN** once the AppView indexes the new setting, it holds nothing from their segment tracks, even before the deletes reach it

#### Scenario: Stray track
- **WHEN** an athlete's `self` space holds a segment track while their setting says matching is off
- **THEN** the AppView doesn't read or use it

#### Scenario: Efforts dropped with the tracks
- **WHEN** an athlete with efforts on several segments turns matching off
- **THEN** once the AppView indexes the new setting, it holds and serves none of those efforts

#### Scenario: Candidate riders dropped with the tracks
- **WHEN** an athlete counted as a rider of several staged candidates turns matching off
- **THEN** once the AppView indexes the new setting, it holds no record that they rode any of them

### Requirement: The AppView never serves segment tracks
The AppView SHALL NOT serve a segment track, its points, or the matching setting to anyone, the athlete included. Only the athlete and the AppView SHALL be able to read them.

#### Scenario: Another viewer
- **WHEN** a mutual of the athlete opens one of the athlete's Public activities with a segment track
- **THEN** the views they get hold no points from the segment track and no link to it

### Requirement: A segment track keeps its leaderboard exclusion
A segment track SHALL say whether its activity is excluded from leaderboards, not excluded when absent. Rewriting a segment track for the same activity, as when it is trimmed again by changed zones, SHALL keep the mark. A segment track written for a new activity, or for a rebuilt combined activity, SHALL start unexcluded. Turning matching off deletes the segment tracks, so it SHALL forget every mark.

#### Scenario: Re-trimmed by new zones
- **WHEN** an athlete excludes a ride, then adds a privacy zone, and the ride's segment track is rewritten
- **THEN** the new segment track is still excluded

#### Scenario: Matching off and on
- **WHEN** an athlete who excluded a ride turns matching off and later on again
- **THEN** the ride's new segment track isn't excluded

### Requirement: Saving the leaderboard choice keeps the matching setting
`actor.segmentPrefs` SHALL also hold whether the athlete hides from leaderboards, not hidden when absent. Saving either setting SHALL keep the other as stored.

#### Scenario: Turn matching off
- **WHEN** an athlete who hides from leaderboards turns matching off
- **THEN** their `actor.segmentPrefs` still says they hide from leaderboards
