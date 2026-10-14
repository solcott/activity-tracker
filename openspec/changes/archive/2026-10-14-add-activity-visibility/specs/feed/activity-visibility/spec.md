# Spec Delta

## Purpose

Lets an athlete choose who sees each activity and its charts (Public, Followers, Mutuals or Only me), set defaults for new activities, and keeps every non-public activity out of their public repo.

## ADDED Requirements

### Requirement: Each activity has one visibility
Every activity SHALL have exactly one visibility: Public, Followers, Mutuals or Only me (`public`, `followers`, `mutuals`, `self`). An activity's visibility SHALL be determined by where its record is stored, not by a field in the record.

#### Scenario: Visibility shown on the activity
- **WHEN** a viewer who may see an activity opens it or sees it in a feed
- **THEN** the activity view states its visibility

#### Scenario: Unknown tier
- **WHEN** an activity record sits in a tier space whose key isn't one of the four tiers
- **THEN** no viewer other than the athlete is shown the activity

### Requirement: Activity stored by visibility
A Public activity SHALL be a `feed.activity` record in the athlete's repo. A Followers, Mutuals or Only-me activity SHALL be a `feed.activity` record in the athlete's `followers`, `mutuals` or `self` tier space, and SHALL NOT be in the repo. An activity SHALL be stored in exactly one place once a save completes.

#### Scenario: Followers activity published
- **WHEN** an athlete publishes an activity with visibility Followers
- **THEN** the activity is a record in their `followers` space, and their repo has no `feed.activity` record with its rkey

#### Scenario: Public activity published
- **WHEN** an athlete publishes an activity with visibility Public
- **THEN** the activity is a record in their repo and in no tier space

#### Scenario: First non-public activity creates the space
- **WHEN** an athlete with no `mutuals` space publishes a Mutuals activity
- **THEN** the `mutuals` space is created with the AppView as managing app, and the activity is saved in it

### Requirement: An activity keeps its identity across tiers
An activity SHALL be identified by `at://{did}/app.activitytracker.feed.activity/{rkey}` wherever its record is stored, and changing its visibility SHALL NOT change its rkey or that URI.

#### Scenario: Link survives a visibility change
- **WHEN** an athlete changes an activity from Public to Followers
- **THEN** the activity's URI is unchanged, and a follower opening a link to it sees the activity

### Requirement: Changing visibility never loses the activity
When an athlete changes an activity's visibility, the record SHALL be written to its new place before it is removed from its old one. If the change fails part-way, the activity SHALL still be stored in at least one place.

#### Scenario: Interrupted move
- **WHEN** an athlete changes an activity from Mutuals to Only me and the removal from `mutuals` fails
- **THEN** the activity is still stored, in the `self` space, the `mutuals` space or both, and a retry finishes the move

#### Scenario: Briefly in two places
- **WHEN** an activity is stored in two places mid-move
- **THEN** each viewer sees it at most once, at the narrower of the two visibilities

### Requirement: Narrowing a public activity warns about published copies
When an athlete changes a Public activity to any other visibility, the client SHALL tell them that copies already published can't be recalled before the change is saved.

#### Scenario: Public to Only me
- **WHEN** an athlete chooses Only me for a Public activity in edit
- **THEN** the client shows the warning, and the change is saved only if they confirm

#### Scenario: Between non-public tiers
- **WHEN** an athlete changes an activity from Followers to Mutuals
- **THEN** no warning is shown

### Requirement: Charts are never more visible than their activity
An activity's charts SHALL have their own visibility, which SHALL be the activity's or narrower, in the order Public, Followers, Mutuals, Only me. Narrowing the activity below its charts' visibility SHALL narrow the charts to match.

#### Scenario: Public run, private heart rate
- **WHEN** an athlete publishes a Public activity with charts set to Only me
- **THEN** any viewer sees the activity, and only the athlete sees its charts

#### Scenario: Wider charts not offered
- **WHEN** an athlete picks charts visibility for a Mutuals activity
- **THEN** only Mutuals and Only me are offered

#### Scenario: Narrowing the activity drags charts
- **WHEN** an activity is Public with Public charts, and the athlete changes the activity to Followers
- **THEN** the charts become Followers too

### Requirement: Non-public charts stored by visibility
An activity's charts (`feed.activityStreams`) whose visibility isn't Public SHALL be stored in the athlete's tier space for that visibility, not in the repo. Moving charts SHALL write them to the new place before removing them from the old one.

#### Scenario: Followers charts
- **WHEN** an athlete publishes charts with visibility Followers
- **THEN** the streams record and its data are in the `followers` space, and the repo has no streams record with the activity's rkey

### Requirement: Default visibilities
An athlete SHALL be able to set a default activity visibility and a default charts visibility in Settings. The default charts visibility SHALL never be wider than the default activity visibility. Both SHALL be Public until the athlete sets them.

#### Scenario: Never set
- **WHEN** an athlete who never set defaults starts a new activity
- **THEN** the activity and its charts start as Public

#### Scenario: Charts default capped
- **WHEN** an athlete sets the default activity visibility to Mutuals while the default charts visibility is Public
- **THEN** the default charts visibility becomes Mutuals

### Requirement: Defaults follow the athlete across devices
The defaults SHALL be stored in a record in the athlete's `self` space, so every client the athlete signs in on uses the same defaults.

#### Scenario: Set on one device
- **WHEN** an athlete sets the default to Followers on desktop and later records an activity on their phone
- **THEN** the phone's new activity starts as Followers

#### Scenario: Defaults unreadable
- **WHEN** the client can't read the defaults record (for example, the PDS can't be reached)
- **THEN** new activities start as Public, without blocking the athlete from publishing

### Requirement: Visibility chosen for each new activity
Recording, manual entry and import (including batch import) SHALL each show a visibility picker for the activity and one for its charts, prefilled from the defaults, on every platform. Changing them SHALL affect only that activity (or batch).

#### Scenario: Override the default
- **WHEN** an athlete whose default is Followers sets one manual entry to Public
- **THEN** that activity is published as Public, and the default stays Followers

#### Scenario: Batch import
- **WHEN** an athlete imports several dives at once with visibility Mutuals
- **THEN** every activity in the batch is published as Mutuals

### Requirement: Visibility editable after publishing
Editing an activity SHALL let the athlete change its visibility and its charts visibility, subject to the charts cap.

#### Scenario: Edit visibility
- **WHEN** an athlete opens edit for their Public activity and saves it as Only me
- **THEN** after confirming the warning, the activity is stored in their `self` space and other viewers no longer see it

### Requirement: Recordings share their primary's visibility
An activity that is another recording of a primary (`recordingOf`) SHALL have the primary's visibility. Grouping a recording SHALL change its visibility to the primary's, and changing a primary's visibility SHALL change its recordings' to match. A `recordingOf` link between activities of different visibilities SHALL be ignored.

#### Scenario: Group into a Followers activity
- **WHEN** an athlete groups a Public activity as a recording of their Followers activity
- **THEN** the recording becomes Followers

#### Scenario: Mismatched link
- **WHEN** a Public activity's `recordingOf` names the athlete's Only-me activity
- **THEN** feeds treat the Public activity as an activity in its own right, and show nothing about the Only-me one

### Requirement: No kudos or comments on non-public activities
Clients SHALL NOT offer kudos or comments on an activity that isn't Public. The AppView SHALL NOT count, list or serve kudos or comments whose subject isn't Public, including those given while it was Public.

#### Scenario: Followers activity in the feed
- **WHEN** a follower sees an athlete's Followers activity
- **THEN** no kudos or comment action is shown, and no kudos or comment counts are shown

#### Scenario: Activity made private after kudos
- **WHEN** an activity with kudos is changed from Public to Followers
- **THEN** its view shows no kudos, and `getKudos` for it returns none

#### Scenario: Made public again
- **WHEN** that activity is changed back to Public
- **THEN** the kudos given while it was Public are shown again

### Requirement: Existing activities stay Public
Activities published before this change SHALL remain Public, with their current charts visibility, until the athlete changes them.

#### Scenario: Old activity
- **WHEN** a viewer opens an activity published before visibility existed
- **THEN** it is shown as Public
