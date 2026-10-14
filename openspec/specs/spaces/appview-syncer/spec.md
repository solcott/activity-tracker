# spaces/appview-syncer Specification

## Purpose

Defines how the AppView joins athletes' tier spaces as a member and syncer, keeps the activities and charts in them indexed, and serves each one only to the viewers its tier admits.

## Requirements

### Requirement: AppView signing identity
The AppView SHALL hold a signing key, configured outside the code, and publish it in its `did:web` document as the `#atproto` verification method. It SHALL use that key to sign Spaces delegation tokens issued as its own DID.

#### Scenario: Key in the DID document
- **WHEN** a PDS resolves the AppView's DID document
- **THEN** it contains a verification method `#atproto` whose public key verifies the AppView's delegation tokens

#### Scenario: Key missing
- **WHEN** the AppView starts without a signing key configured
- **THEN** it serves public data as before, logs that space syncing is disabled, and joins no space

### Requirement: Syncer service in the DID document
The AppView's DID document SHALL list a syncer service whose endpoint is the AppView's public base URL. This service id is the one it registers for notifications.

#### Scenario: Syncer entry listed
- **WHEN** a client fetches `/.well-known/did.json`
- **THEN** the `service` array contains the syncer entry, and the existing `#activity_appview` and `#activitytracker_space` entries are unchanged

### Requirement: Clients announce tier spaces
The AppView SHALL serve `app.activitytracker.space.notifySpace`, a procedure authenticated with the athlete's service auth, that names one of the caller's `app.activitytracker.space.athlete` spaces. The AppView SHALL then join and sync that space.

#### Scenario: Athlete announces a new space
- **WHEN** an athlete's client creates their `followers` space and calls `notifySpace` for it
- **THEN** the AppView obtains a credential for it, registers for notifications, and indexes its records

#### Scenario: Someone else's space
- **WHEN** a caller announces a space whose authority isn't the caller
- **THEN** the AppView rejects the request and joins nothing

#### Scenario: Repeated announcement
- **WHEN** a client announces a space the AppView already syncs
- **THEN** the call succeeds and nothing is duplicated

### Requirement: AppView joins as a member
To read a tier space, the AppView SHALL obtain a space credential from the space's authority PDS using a delegation token it signs as itself, and SHALL reuse the credential until shortly before it expires.

#### Scenario: Credential for a tier space
- **WHEN** the AppView joins alice's `mutuals` space
- **THEN** alice's PDS asks `checkUserAccess` about the AppView's DID, the AppView admits itself, and the AppView holds a credential for that space

### Requirement: Registered for write notifications
The AppView SHALL register with `com.atproto.space.registerNotify` for every space it syncs, and SHALL renew each registration before it expires.

#### Scenario: Registration renewed
- **WHEN** a registration is due to expire within the renewal margin
- **THEN** the AppView registers again, and notifications keep arriving

### Requirement: Writes indexed on notification
The AppView SHALL accept `com.atproto.space.notifyWrite` callouts, which it SHALL authenticate as service auth from the space's authority addressed to the syncer service. It SHALL then fetch the space's ops since the last revision it indexed, and apply them in order.

#### Scenario: Followers activity appears
- **WHEN** alice puts an activity in her `followers` space
- **THEN** after the notification, an effective follower of alice sees it in their timeline

#### Scenario: Deleted from a space
- **WHEN** alice deletes an activity from her `mutuals` space
- **THEN** after the notification, the AppView no longer serves it at the Mutuals tier

#### Scenario: Forged notification
- **WHEN** a `notifyWrite` arrives with no valid service auth, or from a DID that isn't the space's authority
- **THEN** the AppView rejects it and fetches nothing

#### Scenario: Duplicate notification
- **WHEN** the same notification is delivered twice
- **THEN** the index is the same as after one delivery

### Requirement: Periodic reconcile
The AppView SHALL periodically fetch every synced space's ops since the last indexed revision, so that writes whose notifications were lost are indexed without a new write.

#### Scenario: Notification lost
- **WHEN** the AppView was down for longer than the PDS retries a notification, and alice wrote to a space meanwhile
- **THEN** the next reconcile indexes alice's write

### Requirement: Space sync failures are contained
A failure to sync one space, including a revoked or refused credential or a deleted space, SHALL NOT stop the AppView from syncing other spaces or from indexing Jetstream. A space reported as deleted SHALL stop being synced, and its records SHALL be removed from the index.

#### Scenario: Space deleted
- **WHEN** alice deletes her `mutuals` space
- **THEN** the AppView stops syncing it and no longer serves activities from it

#### Scenario: PDS unreachable
- **WHEN** one athlete's PDS can't be reached during reconcile
- **THEN** other spaces reconcile normally, and that space is retried later

### Requirement: Only activity data is indexed from spaces
From tier spaces (`followers`, `mutuals`, `self`), the AppView SHALL index only `feed.activity` and `feed.activityStreams` records. From `interactions` spaces it SHALL index only `feed.like` and `feed.comment` records. It SHALL NOT store `actor.privateDetails`, `graph.followDecline` or `actor.activityDefaults` records from any space.

#### Scenario: Private details ignored
- **WHEN** a synced space's ops include an `actor.privateDetails` write
- **THEN** the AppView stores nothing from it

#### Scenario: Like in a tier space ignored
- **WHEN** a synced `followers` space's ops include a `feed.like` write
- **THEN** the AppView indexes no like from it

#### Scenario: Comment in a tier space ignored
- **WHEN** a synced `followers` space's ops include a `feed.comment` write
- **THEN** the AppView indexes no comment from it

#### Scenario: Activity in an interactions space ignored
- **WHEN** a synced `interactions` space's ops include a `feed.activity` write
- **THEN** the AppView indexes no activity from it

### Requirement: Interactions spaces synced
The AppView SHALL accept `notifySpace` for the caller's own `interactions` space, and SHALL join, register for and sync it as it does a tier space, including notifications, the periodic reconcile and failure containment.

#### Scenario: Athlete announces their interactions space
- **WHEN** an athlete's client creates their `interactions` space and calls `notifySpace` for it
- **THEN** the AppView obtains a credential for it, registers for notifications, and indexes the likes in it

#### Scenario: Someone else's interactions space
- **WHEN** a caller announces an `interactions` space whose authority isn't the caller
- **THEN** the AppView rejects the request and joins nothing

### Requirement: Likes indexed from interactions spaces
A `feed.like` written to an athlete's `interactions` space SHALL be indexed as that athlete's like on its subject, and deleting it SHALL remove only that like. Deleting the space SHALL remove every like indexed from it and no others.

#### Scenario: Like appears
- **WHEN** bob puts a `feed.like` for alice's Followers activity in his `interactions` space, and bob is alice's effective follower
- **THEN** after the notification, the activity's like count includes bob for alice's effective followers

#### Scenario: Like deleted
- **WHEN** bob deletes that like from his `interactions` space
- **THEN** after the notification, it's no longer counted or listed

#### Scenario: Interactions space deleted
- **WHEN** bob deletes his `interactions` space
- **THEN** the AppView stops syncing it and no longer counts the likes indexed from it, while bob's repo likes on Public activities stay counted

#### Scenario: Duplicate notification
- **WHEN** the same notification for bob's like is delivered twice
- **THEN** the like is counted once

### Requirement: Comments indexed from interactions spaces
A `feed.comment` written to an athlete's `interactions` space SHALL be indexed as that athlete's comment on its subject, with the audience it records. Deleting it SHALL remove only that comment. Deleting the space SHALL remove every comment indexed from it and no others.

#### Scenario: Comment appears
- **WHEN** bob puts a `feed.comment` written for Followers on alice's Followers activity in his `interactions` space, and bob is alice's effective follower
- **THEN** after the notification, the comment is counted and listed for alice and her effective followers

#### Scenario: Comment deleted
- **WHEN** bob deletes that comment from his `interactions` space
- **THEN** after the notification, it's no longer counted or listed

#### Scenario: Delete from the other place
- **WHEN** bob deletes the same rkey from his repo, where it isn't
- **THEN** the comment indexed from his `interactions` space stays

#### Scenario: Interactions space deleted
- **WHEN** bob deletes his `interactions` space
- **THEN** the AppView no longer counts or lists the comments indexed from it, while bob's repo comments on Public activities stay

#### Scenario: Backfill
- **WHEN** the AppView first syncs an `interactions` space that already holds comments
- **THEN** it indexes those comments along with the space's likes

#### Scenario: Duplicate notification
- **WHEN** the same notification for bob's comment is delivered twice
- **THEN** the comment is listed once

### Requirement: Non-public activities served by tier
The AppView SHALL serve an activity held in a tier space only to the athlete and to viewers the tier admits: effective followers for `followers`, mutuals by effective follows for `mutuals`, and only the athlete for `self`. This SHALL hold for the timeline, an athlete's activities (both orders), `getActivity`, activity counts and recordings.

#### Scenario: Effective follower's timeline
- **WHEN** bob's follow of alice is effective and alice posts a Followers activity
- **THEN** it appears in bob's timeline and in alice's activities when bob views them

#### Scenario: Pending follower
- **WHEN** bob's follow of alice awaits approval
- **THEN** none of alice's Followers or Mutuals activities appear for bob, and `getActivity` for one answers not found

#### Scenario: Only me
- **WHEN** alice posts an Only-me activity
- **THEN** it appears in alice's own activities and timeline, and for every other viewer `getActivity` for it answers not found

#### Scenario: Activity count per viewer
- **WHEN** alice has 3 Public, 2 Followers and 1 Only-me activities, and bob is her effective follower but not her mutual
- **THEN** alice's profile shows bob 5 activities, shows a non-follower 3, and shows alice 6

#### Scenario: Unauthenticated viewer
- **WHEN** a request without a viewer asks for alice's activities
- **THEN** only her Public activities are returned

#### Scenario: Filters and paging still hold
- **WHEN** a follower pages through a filtered timeline that mixes Public and Followers activities
- **THEN** each page contains only activities the follower may see that match the filter, and the cursor continues the same sequence

### Requirement: Space-held media and charts served by tier
Photos and track of a non-public activity, and charts stored in a tier space, SHALL be served only to viewers who may see them. The AppView SHALL fetch them from the space with its own credential rather than redirecting the viewer to the PDS.

#### Scenario: Follower views a photo
- **WHEN** an effective follower opens a Followers activity with a photo
- **THEN** the photo loads

#### Scenario: Non-follower requests a photo URL
- **WHEN** a viewer who may not see that activity requests its photo's URL
- **THEN** the AppView doesn't serve the photo
