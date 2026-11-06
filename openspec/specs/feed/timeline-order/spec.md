# feed/timeline-order Specification

## Purpose

Orders the timeline so that recent activities surface while old ones, such as a history brought in by a batch import, sink to when they happened, and lists an athlete's own activities by when they happened.

## Requirements

### Requirement: Timeline position
The timeline SHALL list items newest first by feed position. An activity's feed position SHALL be the earliest of when it was posted, when the AppView indexed it and 5 days after it ended.

#### Scenario: Live recording
- **WHEN** an athlete posts a ride a minute after finishing it
- **THEN** it is placed at its post time, above every activity posted before it

#### Scenario: Watch synced late
- **WHEN** an athlete posts a run 3 days after it ended
- **THEN** it is placed at its post time, at the top of followers' timelines

#### Scenario: Old activity imported
- **WHEN** an athlete posts today a ride that ended in March 2019
- **THEN** it is placed 5 days after it ended, below every activity whose feed position is later than that

#### Scenario: Post time in the future
- **WHEN** an activity's record says it was posted after the AppView indexed it
- **THEN** it is placed no later than when it was indexed

### Requirement: Old activities sort by when they happened
Activities placed by when they ended SHALL appear in the order they ended, whatever order they were posted in.

#### Scenario: Batch saved out of order
- **WHEN** a batch saves a combined ride from June 2021 before a run from May 2021, both more than 5 days old
- **THEN** the timeline lists the June ride above the May run

### Requirement: End time comes from the activity's own record
An activity SHALL be taken to end at its start plus its elapsed time, as its own record gives them. When its record has no elapsed time, it SHALL be taken to end when it started. The position SHALL NOT use details from any narrower tier.

#### Scenario: Elapsed time in a narrower tier
- **WHEN** a public activity's elapsed time is shared only with followers
- **THEN** its feed position is computed from its start alone, for every viewer

### Requirement: Combining doesn't resurface an old activity
Combining a copy into an activity SHALL NOT move that activity above where its own end time places it.

#### Scenario: Old activity combined with a copy
- **WHEN** an athlete imports a copy of a ride they logged a year ago and combines it with that ride
- **THEN** the combined ride stays at its place a year back, not at the top of the timeline

### Requirement: Existing activities are re-sorted
Activities indexed before this change SHALL take their feed position from the same rule, without being re-indexed.

#### Scenario: Earlier import
- **WHEN** the AppView starts with this change after an athlete had already imported 200 old activities
- **THEN** those activities are placed by their end times, not at the top of the timeline

### Requirement: An athlete's activities are listed by start
An athlete's activities (their profile) SHALL be listed newest started first, filtered or not, and never by when they were posted.

#### Scenario: Old activity on a profile
- **WHEN** an athlete imports a ride from March 2019
- **THEN** their profile lists it between their activities from before and after it in March 2019

#### Scenario: Profile paging
- **WHEN** a client pages through an athlete's activities with each response's cursor
- **THEN** every activity appears once, in start order, with no gaps

### Requirement: Post position
A post's feed position SHALL be the earlier of when its record says it was created and when the AppView indexed it. Rewriting a post, or changing its audience, SHALL NOT change its position.

#### Scenario: Shared proposal
- **WHEN** alice shares a proposal at noon and a follower refreshes at 12:05
- **THEN** the post is placed at noon, above activities posted before it

#### Scenario: Created time in the future
- **WHEN** a post's record says it was created after the AppView indexed it
- **THEN** it is placed no later than when it was indexed
