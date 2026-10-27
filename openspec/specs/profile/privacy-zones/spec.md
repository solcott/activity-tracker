# profile/privacy-zones Specification

## Purpose

Keeps the places an athlete chooses, such as home and work, and the first and last part of their routes, out of every polyline anyone else can read, while the athlete still sees their own full routes.

## Requirements

### Requirement: Privacy zones are stored only in the athlete's self space
An athlete's privacy zones and their hide-start/end distance SHALL be one `actor.privacyZones` record in the athlete's `self` space. They SHALL NOT be in the athlete's repo or any other space, and the AppView SHALL NOT index or serve them.

#### Scenario: Save a zone
- **WHEN** an athlete saves a privacy zone
- **THEN** their `self` space holds an `actor.privacyZones` record with it, and their repo holds no record naming the zone

#### Scenario: Another device
- **WHEN** an athlete saves a zone on one device and signs in on another
- **THEN** the other device trims their new activities by that zone

### Requirement: A zone is a circle with a chosen radius
A zone SHALL be a center and a radius of 200 m to 1.6 km in 200 m steps, with an optional label. An athlete SHALL be able to keep up to 10 zones.

#### Scenario: Radius out of range
- **WHEN** a client reads a zone whose radius is below 200 m or above 1.6 km
- **THEN** it trims by that radius clamped into the range

#### Scenario: Eleventh zone
- **WHEN** an athlete with 10 zones tries to add another
- **THEN** the app refuses and keeps the 10 they have

### Requirement: A zone's stored center is offset
When a zone is saved, its stored center SHALL be moved from the place the athlete chose by a random distance of at most half its radius, in a random direction. The place the athlete chose SHALL NOT be stored anywhere.

#### Scenario: Save from a point
- **WHEN** an athlete adds a 400 m zone at a point
- **THEN** the stored center is within 200 m of that point, and the point itself is in no record

#### Scenario: Repeated activities
- **WHEN** an athlete publishes many activities that start at the same place inside a zone
- **THEN** their polylines all stop at the circle around the offset center, so the hole they leave is not centered on that place

### Requirement: Hide start and end is off by default
An athlete SHALL be able to set a hide-start/end distance of 200 m to 1.6 km in 200 m steps, or turn it off. It SHALL be off for an athlete who has never set it.

#### Scenario: New athlete
- **WHEN** an athlete with no `actor.privacyZones` record publishes a GPS activity outside any zone
- **THEN** its polyline starts and ends where the track does

#### Scenario: Distance set
- **WHEN** an athlete with hide-start/end at 400 m publishes a GPS activity
- **THEN** its polyline begins 400 m along the track from its start and ends 400 m along it before its end

### Requirement: Polylines are trimmed before they are written
Every polyline an activity record carries SHALL leave out the parts of its track inside any of the athlete's zones and within the hide-start/end distance of either end, cut where the track crosses the boundary. This SHALL hold at every visibility, Only me included, for recordings and imports alike.

#### Scenario: Recording starts at home
- **WHEN** an athlete records an activity that starts and ends inside a zone
- **THEN** its polyline starts and ends on the zone's boundary, and no point of it is inside the zone

#### Scenario: Route passes through a zone
- **WHEN** an imported activity's track enters a zone midway and leaves it again
- **THEN** its polyline has no point inside the zone, and goes straight from where the track entered to where it left

#### Scenario: Only me activity
- **WHEN** an athlete publishes an Only me activity that starts inside a zone
- **THEN** the polyline in its record in the `self` space is trimmed, as a Public one would be

#### Scenario: Whole track hidden
- **WHEN** every point of an activity's track is inside a zone or within the hide-start/end distance
- **THEN** its record has no polyline

### Requirement: Trimming changes only the polyline
Trimming SHALL NOT change an activity's totals (distance, moving and elapsed time, elevation, speed), its streams, its full track or its exported files.

#### Scenario: Totals kept
- **WHEN** an athlete publishes a 10 km run whose first 400 m are inside a zone
- **THEN** its record still says 10 km, and its streams still cover the whole run

#### Scenario: Full track kept
- **WHEN** an athlete publishes a GPS activity that starts inside a zone
- **THEN** its `feed.activityTrack` in `self` holds every point, inside the zone included

### Requirement: The athlete sees their full route
On the athlete's own activity, the detail map SHALL draw the full track from their `self` space when it can be read, and the polyline otherwise. Every other viewer SHALL see only the trimmed polyline.

#### Scenario: Own activity
- **WHEN** an athlete opens their own activity that started inside a zone
- **THEN** the map draws the route from its real start

#### Scenario: Someone else's view
- **WHEN** a mutual opens the same activity
- **THEN** the map draws the trimmed polyline

#### Scenario: Track unreadable
- **WHEN** an athlete opens their own activity offline, or the track can't be read
- **THEN** the map draws the trimmed polyline

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

### Requirement: Adding a zone from an activity or a location
An athlete SHALL be able to add a zone at the real start or end of one of their own GPS activities, and on phones at their current location. The app SHALL NOT ask for any other athlete's activities.

#### Scenario: Hide this start
- **WHEN** an athlete chooses to hide the start of their own activity that has a track
- **THEN** a zone is added around the track's first point, offset as any zone is

#### Scenario: Current location on a phone
- **WHEN** an athlete adds a zone at their current location on a phone
- **THEN** a zone is added around that location, offset as any zone is

#### Scenario: Desktop or web
- **WHEN** an athlete opens privacy zones on desktop or the web
- **THEN** they can add a zone from an activity, and no current-location option is offered

### Requirement: Saving zones is a direct call
Saving zones SHALL write to the PDS at once and report a failure, not queue the save for later. A failed save SHALL leave the earlier zones in force.

#### Scenario: Save offline
- **WHEN** an athlete saves a zone without a connection
- **THEN** the app shows the save failed, and new activities are still trimmed by the earlier zones

### Requirement: Zones that can't be read never widen a route
When a session can't read the athlete's zones, publishing SHALL trim by the zones last read this session. If none were read, the activity SHALL be written with no polyline, never an untrimmed one, and SHALL get a trimmed one once the zones are read. No segment track SHALL be written until the zones are read in this session.

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

### Requirement: A session without the space scope doesn't know the zones
A session signed in before the `space:` scope can't read zones another session saved, so its activities SHALL be written with no polyline, and its zones screen SHALL ask the athlete to sign in again instead of showing no zones. A PDS without Spaces holds no zones, so nothing is trimmed there.

#### Scenario: Session from before the space scope
- **WHEN** an athlete saved a home zone on their phone and records a Public run on a tablet signed in before the `space:` scope existed
- **THEN** the run is written with no polyline, and the tablet's zones screen asks them to sign in again instead of showing no zones

#### Scenario: PDS without Spaces
- **WHEN** an athlete on a PDS without Spaces records a Public run
- **THEN** its polyline is written untrimmed, since no zones can be stored there
