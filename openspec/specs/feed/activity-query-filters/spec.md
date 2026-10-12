# feed/activity-query-filters Specification

## Purpose

Lets clients narrow the AppView's activity feeds (the viewer's timeline and an athlete's activities) by activity type and by when activities started, filtered server-side so every page honors the filter.

## Requirements

### Requirement: Feed queries accept optional filters
`feed.getTimeline` and `feed.getAuthorActivities` SHALL accept optional `type` (string array), `since` (datetime) and `until` (datetime) parameters. When a request omits all three, the results SHALL be identical to the unfiltered query.

#### Scenario: No filter parameters
- **WHEN** a client calls `feed.getTimeline` without `type`, `since` or `until`
- **THEN** the response contains the same activities, in the same order, as before this change

#### Scenario: Timeline still includes the viewer
- **WHEN** a viewer calls `feed.getTimeline` with any filter
- **THEN** matching activities by the viewer appear alongside matching activities by athletes they follow

### Requirement: Type filter matches sport or sport and sub-sport
Each `type` value SHALL be either a sport (`run`) or a sport and sub-sport joined by a colon (`run:trail`). A sport-only value SHALL match every activity of that sport, whatever its sub-sport. A `sport:subSport` value SHALL match only activities with exactly that sport and sub-sport. An activity SHALL be returned when it matches any `type` value.

#### Scenario: Sport matches all sub-sports
- **WHEN** a client filters with `type=run`
- **THEN** activities of sport `run` are returned, both with no sub-sport and with any sub-sport such as `trail`

#### Scenario: Sport and sub-sport matches exactly
- **WHEN** a client filters with `type=run:trail`
- **THEN** only `run` activities with sub-sport `trail` are returned

#### Scenario: Multiple types are OR'd
- **WHEN** a client filters with `type=ride` and `type=run:trail`
- **THEN** every `ride` activity and every `run:trail` activity is returned, and no others

#### Scenario: Unknown type matches nothing
- **WHEN** a client filters only with a well-formed `type` value that no activity has
- **THEN** the response is an empty list, not an error

### Requirement: Date filter matches on start time
`since` (inclusive) and `until` (exclusive) SHALL be compared with each activity's start time, not when it was posted or indexed. Either bound MAY be given alone. Results SHALL stay ordered newest-posted first, as unfiltered feeds are.

#### Scenario: Activity backfilled after the range
- **WHEN** an activity that started inside [since, until) was posted after `until`
- **THEN** it is included in the results

#### Scenario: Only a lower bound
- **WHEN** a client passes `since` without `until`
- **THEN** every activity that started at or after `since` is returned

### Requirement: Filters combine with AND
When both a type filter and a date filter are given, an activity SHALL be returned only if it matches both.

#### Scenario: Type and date together
- **WHEN** a client filters with `type=ride` and a `since`/`until` range
- **THEN** only `ride` activities that started within the range are returned

### Requirement: Paging honors filters
Each page of a filtered query, and its cursor, SHALL contain only activities matching the filter. A cursor SHALL continue the same filtered sequence when the client repeats the same filter.

#### Scenario: Second page stays filtered
- **WHEN** a client requests the next page with the cursor from a filtered first page and the same filter parameters
- **THEN** the next page contains only matching activities, with no duplicates or gaps relative to the first

### Requirement: Filter input is validated
The AppView SHALL treat filter parameters as untrusted. It SHALL reject with `InvalidRequest` a request with more than 20 `type` values, a `type` value that is empty, longer than 129 characters, or has more than one colon or an empty part, or an `until` at or before `since`.

#### Scenario: Too many types
- **WHEN** a client sends 21 `type` values
- **THEN** the AppView responds 400 `InvalidRequest`

#### Scenario: Malformed type
- **WHEN** a client sends `type=run:` or `type=a:b:c`
- **THEN** the AppView responds 400 `InvalidRequest`

#### Scenario: Empty date range
- **WHEN** a client sends an `until` equal to or before `since`
- **THEN** the AppView responds 400 `InvalidRequest`
