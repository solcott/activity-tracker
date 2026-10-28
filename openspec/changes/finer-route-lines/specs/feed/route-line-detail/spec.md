# Spec Delta

## Purpose

Sets how closely the lines maps draw follow the route they stand for: an activity's published polyline and the owner's map of their full track, and how a map draws a line between its points. It also covers bringing activities published at an older detail up to date.

## ADDED Requirements

### Requirement: Polylines follow the track within 2 m
Every point of the track kept in an activity's polyline SHALL be within 2 m of the polyline, as long as that polyline fits the record's 50,000-character limit. Only a polyline that would not fit at 2 m SHALL be coarser, and then only as coarse as it needs to be to fit. This SHALL hold for recorded, imported and combined activities, and for polylines rebuilt by a re-trim.

#### Scenario: Switchbacks kept
- **WHEN** an athlete records a ride up a trail whose switchbacks turn in loops about 10 m wide
- **THEN** the activity's polyline follows each switchback around its turn, rather than cutting across it

#### Scenario: Long ride
- **WHEN** an athlete imports a ride whose polyline would be longer than 50,000 characters at 2 m
- **THEN** its polyline fits in 50,000 characters and is no coarser than it needs to be to fit

#### Scenario: Breaks and trimming kept
- **WHEN** an activity's polyline is built at the finer detail
- **THEN** it is still trimmed by the athlete's privacy zones and hidden ends, and broken where the track has gaps, as before

### Requirement: The owner's map draws the track within 1 m
When an athlete opens one of their own activities and its full track can be read, the detail map and the enlarged map SHALL draw a line that keeps every point of the track within 1 m. That line SHALL NOT be cut down to fit any record limit.

#### Scenario: Own activity
- **WHEN** an athlete opens their own activity that has a track
- **THEN** the map's line keeps every point of the track within 1 m, however long the activity

#### Scenario: Someone else's activity
- **WHEN** a viewer opens another athlete's activity
- **THEN** the map draws that activity's published polyline

### Requirement: Older polylines are rebuilt once
When the app's polyline detail is newer than the detail the athlete's activities were last rebuilt at, a session SHALL rebuild the polyline of each of their activities that has a track, at every visibility, from that track. It SHALL do this once, without the athlete doing anything. A rebuild that doesn't finish SHALL finish in a later session on any of the athlete's devices.

#### Scenario: First session after updating
- **WHEN** an athlete whose activities have 5 m polylines opens an updated app
- **THEN** each of their activities with a track gets a polyline that follows the track within 2 m, trimmed by their current privacy zones

#### Scenario: Rebuild done
- **WHEN** every activity has been rebuilt at the current detail
- **THEN** later sessions don't rebuild them again until the zones or the detail change

#### Scenario: Interrupted
- **WHEN** the app closes before every activity is rebuilt
- **THEN** a later session, on that device or another, rebuilds the rest

#### Scenario: Nothing else changes
- **WHEN** an activity's polyline is rebuilt
- **THEN** its title, description, photos, visibility, totals, segment track and rkey are unchanged, and an activity whose polyline comes out the same isn't rewritten

#### Scenario: Manual entry
- **WHEN** an athlete has a manual entry with no track
- **THEN** it isn't rewritten

#### Scenario: Older app writes meanwhile
- **WHEN** an athlete's other device, on an app without the finer detail, finishes a re-trim and records only that
- **THEN** the next session on an updated app rebuilds the polylines at the current detail again

### Requirement: Maps draw a smooth curve through a line's points
Every map and route preview SHALL draw an activity's route, a segment's line and a highlighted stretch as a smooth curve that passes through each of the line's stored points in order, rather than as straight segments between them. Between two neighbouring points the curve SHALL stay within 2 m of the straight line joining them, and SHALL NOT loop back. Only drawing changes: records, breaks and markers keep the stored points.

#### Scenario: Switchback drawn round
- **WHEN** a viewer zooms in on a segment whose line turns through a hairpin in a few points
- **THEN** the map draws the hairpin as a rounded turn through those points, not as straight lines meeting at angles

#### Scenario: Never cuts a corner
- **WHEN** a line turns sharply at a point
- **THEN** the drawn curve still passes through that point, and between it and its neighbours stays within 2 m of the straight lines joining them

#### Scenario: Straight stretches stay straight
- **WHEN** a line's points lie along a straight road
- **THEN** the map draws a straight line there

#### Scenario: Breaks and markers unchanged
- **WHEN** a route with a break and start and end markers is drawn
- **THEN** the curve stops and resumes at the same points as before, and the markers sit on the stored points
