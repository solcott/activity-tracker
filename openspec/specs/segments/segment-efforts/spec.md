# segments/segment-efforts Specification

## Purpose

Defines segment efforts: how the AppView finds where a consenting athlete's activity rode, ran or skied a segment from start to end, how long it took, when efforts are recomputed or removed, who may see each one, and how activity and segment pages show them.

## Requirements

### Requirement: Efforts come only from usable segment tracks
The AppView SHALL match an activity against segments only from its athlete's segment track, and only while that athlete's segment matching is on. It SHALL NOT match from polylines, streams or anything else, and an activity without a usable segment track SHALL have no efforts.

#### Scenario: Matching off
- **WHEN** an athlete with matching off publishes a ride along a segment
- **THEN** the ride has no effort on it

#### Scenario: Matching turned on later
- **WHEN** an athlete turns matching on and their earlier ride's segment track arrives
- **THEN** the ride gets its efforts

### Requirement: An effort is a pass from start to end
An activity SHALL get an effort on a segment of its sport group each time one piece of its segment track passes within 25 m of the segment's start, then follows the segment's line in order to within 25 m of its end, staying within 30 m of every point of the line. Following the line in reverse SHALL NOT count, and a pass SHALL NOT span two pieces.

#### Scenario: Full pass
- **WHEN** a run's segment track follows a segment from its start to its end
- **THEN** the run gets one effort on it

#### Scenario: Two laps
- **WHEN** a ride does a loop segment twice
- **THEN** the ride gets two efforts on it

#### Scenario: Wrong direction
- **WHEN** a ride covers a segment from its end to its start
- **THEN** the ride gets no effort on it

#### Scenario: Shortcut
- **WHEN** a run leaves a segment's line by more than 30 m in the middle and rejoins it
- **THEN** the run gets no effort on it

#### Scenario: Across a privacy zone
- **WHEN** a segment passes through one of the athlete's privacy zones, so their segment track has a gap there
- **THEN** the activity gets no effort on it

### Requirement: An effort's time is measured from the track
An effort's elapsed time SHALL be the time between the moments the track reaches the segment's start and its end, each interpolated between the samples around it, in whole seconds. An effort SHALL also record when it began, as an offset from the activity's start, and SHALL NOT be stored with a wall-clock time.

#### Scenario: Elapsed time
- **WHEN** a run reaches a segment's start 120.4 s into the run and its end 731.6 s in
- **THEN** its effort's elapsed time is 611 s, starting 120 s into the activity

### Requirement: Members of a combined activity get no efforts
An activity that is a valid member of a combined activity SHALL get no efforts. The combined activity SHALL get them instead, from its own segment track.

#### Scenario: Two recordings combined
- **WHEN** an athlete combines a watch run and a phone run of the same outing
- **THEN** only the combined activity has efforts, so the outing doesn't count twice

### Requirement: Efforts follow their sources
Efforts SHALL be recomputed when an activity's segment track, the activity, or a segment changes. A new segment SHALL be matched against the existing usable segment tracks that pass near it. Deleting an activity, its segment track or a segment, turning the athlete's matching off, deleting their `self` space or deleting their account SHALL remove the efforts that came from it.

#### Scenario: New segment
- **WHEN** a segment is created along a trail that 40 consenting athletes' earlier runs followed
- **THEN** each of those runs gets its effort without the athletes doing anything

#### Scenario: Track re-trimmed
- **WHEN** an athlete adds a privacy zone that cuts through a segment, and their segment tracks are rewritten
- **THEN** their efforts on that segment are removed

#### Scenario: Matching off
- **WHEN** an athlete turns matching off
- **THEN** once the AppView indexes the setting, it serves none of their efforts

### Requirement: An effort is visible at its narrowest tier
An effort SHALL be visible at the narrowest of three visibilities: the activity's, its Map category's and its Pace/Speed category's. The AppView SHALL serve an effort only to viewers admitted to that tier. It SHALL serve nothing about an effort to anyone else, its existence included, and SHALL re-evaluate the tier when any of the three changes.

#### Scenario: Public activity, followers map
- **WHEN** an athlete's Public run has its Map category at Followers
- **THEN** its efforts are shown to the athlete's effective followers and to no one else

#### Scenario: Only me
- **WHEN** an athlete's Only me ride has efforts
- **THEN** only the athlete sees them

#### Scenario: Activity narrowed
- **WHEN** an athlete changes a Public run to Mutuals
- **THEN** its efforts stop being shown to anyone who isn't a mutual

### Requirement: An activity's efforts are listed with it
`feed.getActivitySegments` SHALL list the efforts on an activity that the viewer may see, in the order they began. Each SHALL have the segment's view, the elapsed time, when it began, and whether it is the athlete's fastest effort on that segment among those the viewer may see. An activity the viewer may not see SHALL be `NotFound`.

#### Scenario: Activity page
- **WHEN** a follower opens an athlete's Public ride that passed three segments
- **THEN** they see three efforts, in order, with the fastest of the athlete's visible efforts on each marked

#### Scenario: Hidden activity
- **WHEN** a stranger asks for the segments of an athlete's Followers ride
- **THEN** the AppView answers `NotFound`

### Requirement: An athlete's efforts on a segment are listed
`feed.getSegmentEfforts` SHALL list one athlete's efforts on a segment that the viewer may see, fastest first, with a cursor. Each SHALL name the activity it came from, its elapsed time, and the activity's date.

#### Scenario: Own history
- **WHEN** an athlete opens a segment they've run five times
- **THEN** they see all five efforts, fastest first

#### Scenario: Another athlete's efforts
- **WHEN** a viewer asks for a stranger's efforts on a segment, and two of the stranger's three efforts are on Public activities
- **THEN** they see those two only

### Requirement: The app shows efforts on the activity page
The activity page SHALL list the activity's efforts the viewer is given, each with its segment's name and the effort's time, and mark the athlete's fastest with "PR". An effort on a segment that isn't `active` SHALL also show the segment's status. Tapping one SHALL open the segment screen. An activity with no efforts SHALL show no segments section.

#### Scenario: Activity with efforts
- **WHEN** an athlete opens their own ride with two efforts
- **THEN** the page lists both segments with their times, and opens a segment's screen when one is tapped

#### Scenario: Effort on a proposed segment
- **WHEN** an athlete opens a run with an effort on a proposed segment
- **THEN** the effort is listed and marked as proposed

#### Scenario: Activity without efforts
- **WHEN** a viewer opens an activity with no efforts they may see
- **THEN** the page shows no segments section

### Requirement: The segment screen shows the segment and the viewer's efforts
The segment screen SHALL show the segment's line on a map, its name, length, sport group and status, a link to the original for a `duplicate`, and the viewer's own efforts on it, fastest first.

#### Scenario: Duplicate segment's screen
- **WHEN** an athlete opens a segment marked `duplicate`
- **THEN** the screen shows that it's a duplicate and links to the original

### Requirement: Only proposed and active segments get new efforts
The AppView SHALL match segment tracks only against `proposed` and `active` segments. A segment marked `duplicate` SHALL lose its efforts. An `archived` segment SHALL keep the efforts it has but get no new ones, and a later change to an activity SHALL remove that activity's efforts on it rather than recompute them.

#### Scenario: Duplicate segment
- **WHEN** a ride passes both a segment and a duplicate of it
- **THEN** the ride gets an effort on the original only

#### Scenario: Archived segment
- **WHEN** an athlete rides an archived segment
- **THEN** the ride gets no effort on it, and their earlier efforts on it stay

### Requirement: An athlete's leaderboard entry carries a top-10 rank
In `feed.getActivitySegments`, the effort that is the athlete's entry on the segment's all-time, everyone board of the effort's own type, as `segments/segment-leaderboards` builds it for that viewer, SHALL carry its rank on that board when the rank is 10 or better. That entry is the athlete's fastest ranked effort the viewer may see, so it SHALL carry the rank even when a faster effort of theirs, marked as their PR, isn't ranked. No other effort SHALL carry a rank.

#### Scenario: Third place
- **WHEN** a viewer opens a Public ride whose effort is 3rd on the board they see
- **THEN** the effort carries rank 3

#### Scenario: Ranked among its own type
- **WHEN** a handcycle effort is 6th among all efforts on a ride segment but 1st among handcycles
- **THEN** it carries rank 1

#### Scenario: Outside the top 10
- **WHEN** the ride's effort is 11th on the viewer's board
- **THEN** it carries no rank

#### Scenario: Faster effort excluded
- **WHEN** an athlete's fastest effort is on an activity excluded from leaderboards, and their next fastest is 1st on the board
- **THEN** the next fastest carries rank 1, and the excluded one, still marked PR, carries none

#### Scenario: Proposed segment
- **WHEN** the ride's effort is on a proposed segment
- **THEN** it carries no rank

### Requirement: The athlete learns why an effort isn't ranked
When the viewer is the effort's own athlete and the segment is `active`, an effort that leaderboards don't rank SHALL say why: the athlete hides from leaderboards, the activity is excluded, or the effort is faster than plausible, in that order of precedence. No other viewer SHALL be told.

#### Scenario: Implausible effort
- **WHEN** an athlete opens their run with an effort faster than the run cap
- **THEN** the effort says it's faster than plausible

#### Scenario: Other viewer
- **WHEN** a follower opens the same run
- **THEN** the effort carries neither a rank nor a reason

### Requirement: The activity page shows ranks
The activity page SHALL show an effort's rank, when it has one, beside its "PR" mark, and SHALL show the athlete their own effort's reason for not being ranked.

#### Scenario: Top-10 badge
- **WHEN** a viewer opens a ride whose effort carries rank 2
- **THEN** the effort is shown with "2nd"

#### Scenario: Excluded activity
- **WHEN** an athlete opens their own ride excluded from leaderboards
- **THEN** its efforts say they aren't on leaderboards because the ride is excluded
