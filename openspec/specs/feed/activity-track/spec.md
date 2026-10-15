# feed/activity-track Specification

## Purpose

Defines where an activity's full-resolution GPS track is stored, who may read it, and how it follows the activity's writes, merges and deletes, so that exact positions are never readable by anyone but the athlete.

## Requirements

### Requirement: Activity records carry no full track
A `feed.activity` record SHALL NOT hold the full-resolution track, at any visibility. It SHALL keep only the simplified `polyline` that previews and maps draw.

#### Scenario: Recorded GPS activity
- **WHEN** an athlete publishes a recorded activity with GPS points
- **THEN** its `feed.activity` record has a `polyline` and no track blob

#### Scenario: Imported file
- **WHEN** an athlete imports a GPX, TCX or FIT file with positions
- **THEN** its `feed.activity` record has a `polyline` and no track blob

### Requirement: Full track stored only in the athlete's self space
An activity's full-resolution track SHALL be a `feed.activityTrack` record with the activity's rkey, holding the track as a GPX blob, stored in the athlete's `self` space whatever the activity's visibility. It SHALL NOT be in the athlete's repo or any other space.

#### Scenario: Public activity with GPS
- **WHEN** an athlete publishes a Public activity with GPS points
- **THEN** a `feed.activityTrack` with its rkey is in the athlete's `self` space, and the athlete's repo holds no track record or track blob for it

#### Scenario: Followers activity with GPS
- **WHEN** an athlete publishes a Followers activity with GPS points
- **THEN** its `feed.activityTrack` is in the athlete's `self` space, not the followers space

#### Scenario: Activity without positions
- **WHEN** an athlete publishes a manual entry or an indoor activity without positions
- **THEN** no `feed.activityTrack` is written for it

### Requirement: Only the athlete reads the full track
An activity's full track SHALL be readable only by the athlete who owns it. No other viewer, whatever their relationship to the athlete, SHALL be able to get it, and the AppView SHALL NOT serve it.

#### Scenario: Mutual views a Public activity
- **WHEN** a mutual of the athlete opens one of the athlete's Public activities with GPS
- **THEN** the views they get contain the polyline and no link to the full track, and they can't read the athlete's `self` space

#### Scenario: Public blob fetch
- **WHEN** anyone requests the athlete's repo blobs with `com.atproto.sync.getBlob`
- **THEN** no blob they can fetch holds a full track

### Requirement: Track written with its activity
The full track SHALL be queued with its activity and sent with it, offline or online, so an activity with positions that reaches the PDS gets its track too.

#### Scenario: Publish offline
- **WHEN** an athlete publishes a GPS activity without a connection, then reconnects
- **THEN** both the activity and its `feed.activityTrack` in `self` are written

### Requirement: Visibility changes leave the track in place
Changing an activity's visibility SHALL NOT move or change its full track.

#### Scenario: Public to Only me and back
- **WHEN** an athlete changes a GPS activity from Public to Only me, then back to Public
- **THEN** its `feed.activityTrack` stays in the athlete's `self` space throughout, unchanged

### Requirement: Deleting an activity deletes its track
Deleting an activity SHALL delete its full track. Deleting a main activity with its recordings SHALL delete each recording's track too; recordings split off instead SHALL keep theirs.

#### Scenario: Delete one activity
- **WHEN** an athlete deletes a GPS activity
- **THEN** its `feed.activityTrack` is gone from the athlete's `self` space

#### Scenario: Delete with recordings
- **WHEN** an athlete deletes a main activity together with its recordings
- **THEN** the tracks of the main activity and of every recording are gone

#### Scenario: Delete without recordings
- **WHEN** an athlete deletes a main activity and keeps its recordings
- **THEN** the main activity's track is gone and each recording's track stays

### Requirement: Merging fills a missing track
Merging another recording into an athlete's activity SHALL give the activity that recording's track when the activity has none, and SHALL NOT replace a track the activity already has.

#### Scenario: Activity without a track
- **WHEN** an athlete merges an imported dive with samples into a logged dive that has no track
- **THEN** the logged dive gets a `feed.activityTrack` from the import

#### Scenario: Activity with a track
- **WHEN** an athlete merges a recording into an activity that already has a track
- **THEN** the activity's track is unchanged

#### Scenario: Activity still unsent
- **WHEN** an athlete merges a recording into an activity whose track is still waiting to be sent
- **THEN** the waiting track is the one written, and the merge adds no other

### Requirement: Edits and ungroups keep the track
Editing an activity's title, description, dive site or photos, and splitting a recording off its group, SHALL leave its full track as it is.

#### Scenario: Edit title
- **WHEN** an athlete changes a GPS activity's title
- **THEN** its `feed.activityTrack` is unchanged

#### Scenario: Ungroup a recording
- **WHEN** an athlete splits a recording off its main activity
- **THEN** the recording's `feed.activityTrack` is unchanged
