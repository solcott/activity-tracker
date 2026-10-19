# feed/activity-visibility Specification

## Purpose

Lets an athlete choose who sees each activity and its charts (Public, Followers, Mutuals or Only me), set defaults for new activities, and keeps every non-public activity out of their public repo.

## Requirements

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

### Requirement: Default visibilities
An athlete SHALL be able to set a default activity visibility in Settings, alongside the per-category defaults in `feed/detail-visibility`. It SHALL be Public until the athlete sets it.

#### Scenario: Never set
- **WHEN** an athlete who never set defaults starts a new activity
- **THEN** the activity starts as Public

#### Scenario: Charts default capped
- **WHEN** an athlete sets the default activity visibility to Mutuals while the Heart rate default is Public
- **THEN** the Heart rate default becomes Mutuals

### Requirement: Defaults follow the athlete across devices
The defaults SHALL be stored in a record in the athlete's `self` space, so every client the athlete signs in on uses the same defaults.

#### Scenario: Set on one device
- **WHEN** an athlete sets the default to Followers on desktop and later records an activity on their phone
- **THEN** the phone's new activity starts as Followers

#### Scenario: Defaults unreadable
- **WHEN** the client can't read the defaults record (for example, the PDS can't be reached)
- **THEN** new activities start as Public, without blocking the athlete from publishing

### Requirement: Visibility chosen for each new activity
Recording, manual entry and import (including batch import) SHALL each show a visibility picker for the activity, prefilled from the defaults, on every platform, together with the category pickers in `feed/detail-visibility`. Changing it SHALL affect only that activity (or batch).

#### Scenario: Override the default
- **WHEN** an athlete whose default is Followers sets one manual entry to Public
- **THEN** that activity is published as Public, and the default stays Followers

#### Scenario: Batch import
- **WHEN** an athlete imports several dives at once with visibility Mutuals
- **THEN** every activity in the batch is published as Mutuals

#### Scenario: No charts picker
- **WHEN** an athlete imports a ride with heart rate and power
- **THEN** no single "who can see these charts" picker is shown, and the Heart rate and Power pickers are

### Requirement: Visibility editable after publishing
Editing an activity SHALL let the athlete change its visibility and each category's visibility, subject to the category cap.

#### Scenario: Edit visibility
- **WHEN** an athlete opens edit for their Public activity and saves it as Only me
- **THEN** after confirming the warning, the activity is stored in their `self` space, every category is Only me, and other viewers no longer see it

### Requirement: Recordings share their primary's visibility
An activity that is another recording of a primary (`recordingOf`, a legacy group not yet migrated to a combined activity) SHALL have the primary's visibility, and changing a primary's visibility SHALL change its recordings' to match. A `recordingOf` link between activities of different visibilities SHALL be ignored. Clients SHALL NOT create new `recordingOf` links.

#### Scenario: Legacy group follows its primary
- **WHEN** an athlete narrows a not-yet-migrated primary with one recording from Public to Followers
- **THEN** the recording becomes Followers

#### Scenario: Group into a Followers activity
- **WHEN** a not-yet-migrated legacy group links a recording to the athlete's Followers activity
- **THEN** the recording becomes Followers

#### Scenario: Mismatched link
- **WHEN** a Public activity's `recordingOf` names the athlete's Only-me activity
- **THEN** feeds treat the Public activity as an activity in its own right, and show nothing about the Only-me one

### Requirement: Likes at every visibility
Clients SHALL offer the like action and show the like count on every activity the viewer can see, whatever its visibility, except the viewer's own activities. Liking SHALL keep working for an athlete who can see the activity, at Public, Followers, Mutuals or Only me.

#### Scenario: Follower likes a Followers activity
- **WHEN** an effective follower of an athlete opens one of their Followers activities
- **THEN** the like action and like count are shown, and liking it increments the count and marks it liked for the follower

#### Scenario: Own activity
- **WHEN** an athlete views their own Mutuals activity
- **THEN** the like count is shown, and no like action is offered

#### Scenario: Unlike
- **WHEN** a follower who liked a Followers activity unlikes it
- **THEN** the count goes down and the activity is no longer marked liked for them, without the client needing to know where the like was stored

### Requirement: Where a like is stored
A like on a Public activity SHALL be a `feed.like` record in the giver's repo. A like on a Followers, Mutuals or Only-me activity SHALL be a `feed.like` record in the giver's own `interactions` space, and SHALL NOT be in their repo. A like SHALL stay where it was written when the activity's visibility later changes.

#### Scenario: Like on a non-public activity
- **WHEN** a mutual likes an athlete's Mutuals activity
- **THEN** the giver's repo gains no `feed.like` record, and the like is a record in the giver's `interactions` space

#### Scenario: Like on a Public activity
- **WHEN** a viewer likes a Public activity
- **THEN** the like is a `feed.like` record in the viewer's repo, as before

#### Scenario: Activity moved after the like
- **WHEN** an athlete changes a liked Followers activity to Public
- **THEN** the giver's like stays in their `interactions` space, and the giver's client writes nothing

#### Scenario: Session without the space scope
- **WHEN** an athlete whose session lacks the `space:` scope likes a Followers activity
- **THEN** the like is refused with an error that identifies the missing scope, nothing is written to their repo, and they can still like Public activities

### Requirement: Who sees a like
A like SHALL be counted, listed by `getLikes`, and reported as `viewer.like` for a viewer only while both the viewer and the like's giver may see the activity at its current visibility, wherever the like is stored. Likes SHALL follow the activity when it is narrowed and when it is widened. An athlete SHALL NOT be counted for liking their own activity.

#### Scenario: Narrowed after likes
- **WHEN** a Public activity with likes from a follower and a non-follower is changed to Followers
- **THEN** an effective follower sees the follower's like counted and listed, and not the non-follower's

#### Scenario: Widened after likes
- **WHEN** a Followers activity liked by a follower is changed to Public
- **THEN** any viewer, signed in or not, sees that like counted and listed by `getLikes`

#### Scenario: Giver unfollows
- **WHEN** a follower who liked a Followers activity stops following the athlete, and the AppView has indexed the unfollow
- **THEN** that like is no longer counted or listed for anyone, and it's counted again if they follow again and the follow is effective

#### Scenario: Like from someone who can't see the activity
- **WHEN** a like names a Mutuals activity whose athlete doesn't follow the giver back
- **THEN** it isn't counted or listed for anyone

#### Scenario: Follow indexed after the like
- **WHEN** a new follower's like on a Followers activity reaches the AppView before their follow does
- **THEN** the like is counted once the follow is indexed and effective

#### Scenario: Viewer who can't see the activity
- **WHEN** a viewer who may not see a Followers activity asks `getLikes` for it
- **THEN** the answer reveals no likes

### Requirement: Own non-public photos show before indexing
On the device that published or edited an activity, the athlete's photos on it SHALL show from the bytes that device uploaded until the AppView serves them, at any visibility. A non-public activity's photo SHALL NOT load from the PDS's public blob endpoint, which refuses blobs only a tier space holds. Once the AppView serves a photo, its URL SHALL be shown instead.

#### Scenario: Followers activity edited with a new photo
- **WHEN** an athlete adds a photo to their Followers activity and saves, before the AppView has indexed the edit
- **THEN** the activity shows the new photo on that device, loaded from the bytes it uploaded, and no request for it is made to the PDS

#### Scenario: Photos the AppView already serves
- **WHEN** the athlete edits a Mutuals activity's title, before the AppView has indexed the edit
- **THEN** the photos it already served keep showing from the AppView's URLs

#### Scenario: AppView catches up
- **WHEN** the AppView serves a photo the device had shown from its own bytes
- **THEN** the photo shows from the AppView's URL, and the device stops holding its bytes

#### Scenario: Bytes not held
- **WHEN** the app restarts before the AppView indexes the athlete's Only-me edit with a new photo
- **THEN** that photo shows as an empty placeholder, makes no request to the PDS, and stays on the activity if the athlete edits it again

#### Scenario: Public photo without held bytes
- **WHEN** a Public activity's photo isn't served by the AppView yet and the device doesn't hold its bytes
- **THEN** it loads from the PDS's public blob endpoint

#### Scenario: Other devices
- **WHEN** the athlete opens the activity on another device before the AppView indexes it
- **THEN** that device shows the activity as the AppView serves it, without the photos it hasn't indexed

### Requirement: Served photos matched by blob
The client SHALL recognize a photo the AppView serves by its blob CID whether or not its URL is signed for the viewer, so an athlete editing their non-public activity keeps every photo it has.

#### Scenario: Editing a Followers activity's text
- **WHEN** an athlete edits only the description of an indexed Followers activity with two photos
- **THEN** the edit form shows both photos, and the saved record still holds both
