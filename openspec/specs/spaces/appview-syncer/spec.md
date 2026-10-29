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
To read a tier space, the AppView SHALL obtain a space credential from the space's authority PDS using a delegation token it signs as itself, addressed to the authority's `#atproto_pds` service, and SHALL reuse the credential until shortly before it expires.

#### Scenario: Credential for a tier space
- **WHEN** the AppView joins alice's `mutuals` space
- **THEN** alice's PDS asks `checkUserAccess` about the AppView's DID, the AppView admits itself, and the AppView holds a credential for that space

#### Scenario: Delegation token audience
- **WHEN** the AppView signs a delegation token for a space whose authority is alice
- **THEN** the token's `aud` is alice's DID followed by `#atproto_pds`, and its header `kid` is `#atproto`

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
A failure to sync one space, including a revoked or refused credential or a deleted space, SHALL NOT stop the AppView from syncing other spaces or from indexing Jetstream. A space reported deleted (`SpaceDeleted`) SHALL stop being synced and its records removed. A space answered `SpaceNotFound`, which is also a refusal, SHALL keep its records and back off, and be dropped only after 24 hours of `SpaceNotFound` answers.

#### Scenario: Space deleted
- **WHEN** alice deletes her `mutuals` space
- **THEN** the AppView stops syncing it and no longer serves activities from it

#### Scenario: PDS unreachable
- **WHEN** one athlete's PDS can't be reached during reconcile
- **THEN** other spaces reconcile normally, and that space is retried later

#### Scenario: Refusal while the managing app is unreachable
- **WHEN** alice's PDS can't reach the AppView's `checkUserAccess` for a while and answers the AppView's credential requests for her `followers` space with `SpaceNotFound`
- **THEN** the AppView keeps serving the activities it indexed from that space to the viewers its tier admits, retries with backoff, and resumes syncing once a credential is issued again

#### Scenario: Space missing for a day
- **WHEN** every sync of a space has been answered `SpaceNotFound` for 24 hours
- **THEN** the AppView stops syncing it and removes everything indexed from it

#### Scenario: Not-found streak broken
- **WHEN** a space answered `SpaceNotFound` and a later sync succeeds or fails with another error
- **THEN** the 24 hours start over at the next `SpaceNotFound`

### Requirement: Deletion notifications purge a space at once
The AppView SHALL accept `com.atproto.space.notifySpaceDeleted` callouts, authenticated as service auth from the space's authority addressed to the syncer service. For a space it syncs, it SHALL stop syncing it and remove everything indexed from it before answering. Repeated or unknown notifications SHALL succeed without effect.

#### Scenario: Space deleted
- **WHEN** alice deletes her `followers` space and her PDS notifies the AppView
- **THEN** the AppView no longer serves any activity, like, comment or other record indexed from that space, without waiting for a sync

#### Scenario: Notification repeated
- **WHEN** the same deletion notification arrives twice
- **THEN** both calls succeed and the second changes nothing

#### Scenario: Forged deletion
- **WHEN** a `notifySpaceDeleted` arrives with no valid service auth, or from a DID that isn't the space's authority
- **THEN** the AppView rejects it and keeps syncing and serving the space

#### Scenario: Space recreated
- **WHEN** alice recreates a space at the same URI after it was deleted and her client announces it
- **THEN** the AppView joins it as a new space and indexes only what the new space holds

### Requirement: Only activity data is indexed from spaces
From tier spaces (`followers`, `mutuals`, `self`), the AppView SHALL index only `feed.activity`, `feed.activityDetails`, `feed.activityStreams` and `feed.activityZones` records, and from the `self` space also `feed.segmentTrack` and `actor.segmentPrefs` records.

#### Scenario: Zones indexed from a tier space
- **WHEN** a synced `mutuals` space's ops include a `feed.activityZones` write
- **THEN** the AppView indexes the zones at Mutuals

#### Scenario: Like in a tier space ignored
- **WHEN** a synced `followers` space's ops include a `feed.like` write
- **THEN** the AppView indexes no like from it

#### Scenario: Comment in a tier space ignored
- **WHEN** a synced `followers` space's ops include a `feed.comment` write
- **THEN** the AppView indexes no comment from it

#### Scenario: Comment hide in a tier space ignored
- **WHEN** a synced `followers` space's ops include a `feed.commentHide` write
- **THEN** the AppView indexes no hide from it

#### Scenario: Block in a tier space ignored
- **WHEN** a synced `followers` space's ops include a `graph.block` or `graph.mute` write
- **THEN** the AppView indexes no block or mute from it

#### Scenario: Segment track indexed from self
- **WHEN** a synced `self` space's ops include a `feed.segmentTrack` write
- **THEN** the AppView indexes it as that athlete's segment track for the activity with its rkey

#### Scenario: Segment track outside self ignored
- **WHEN** a synced `followers` or `interactions` space's ops include a `feed.segmentTrack` or `actor.segmentPrefs` write
- **THEN** the AppView indexes nothing from it

### Requirement: Only interactions are indexed from interactions spaces
From `interactions` spaces the AppView SHALL index only `feed.like`, `feed.comment`, `feed.commentHide`, `graph.block` and `graph.mute` records.

#### Scenario: Zones in an interactions space ignored
- **WHEN** a synced `interactions` space's ops include a `feed.activityZones` write
- **THEN** the AppView indexes no zones from it

#### Scenario: Activity in an interactions space ignored
- **WHEN** a synced `interactions` space's ops include a `feed.activity` write
- **THEN** the AppView indexes no activity from it

#### Scenario: Block indexed from an interactions space
- **WHEN** a synced `interactions` space's ops include a `graph.block` write whose rkey is its `subject`
- **THEN** the AppView indexes a block of that subject by the space's authority, and deleting the record removes only that block

#### Scenario: Mute indexed from an interactions space
- **WHEN** a synced `interactions` space's ops include a `graph.mute` write whose rkey is its `subject`
- **THEN** the AppView indexes a mute of that subject by the space's authority, and deleting the record removes only that mute

#### Scenario: Block or mute under another rkey ignored
- **WHEN** a `graph.block` or `graph.mute` in an `interactions` space has an rkey other than its `subject`
- **THEN** the AppView indexes nothing from it

#### Scenario: Interactions space deleted
- **WHEN** an athlete's `interactions` space is reported deleted
- **THEN** every block and mute indexed from it is removed, and their Bluesky blocks still apply

### Requirement: Client-only records are never stored from spaces
The AppView SHALL NOT store `actor.privateDetails`, `graph.followDecline`, `actor.activityDefaults`, `actor.privacyZones`, `actor.routeTrim`, `feed.activityTrack`, `feed.activitySource`, `feed.activityDevices` or `feed.segmentStar` records from any space.

#### Scenario: Private details ignored
- **WHEN** a synced space's ops include an `actor.privateDetails` write
- **THEN** the AppView stores nothing from it

#### Scenario: Full track still ignored
- **WHEN** a synced `self` space's ops include a `feed.activityTrack` or `actor.privacyZones` write
- **THEN** the AppView stores nothing from it

#### Scenario: Segment star ignored
- **WHEN** a synced `self` space's ops include a `feed.segmentStar` write
- **THEN** the AppView stores nothing from it

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

### Requirement: Comment hides indexed from interactions spaces
A `feed.commentHide` written to an athlete's `interactions` space SHALL be indexed as that athlete's hide of the comment it names. Deleting it SHALL remove only that copy of the hide. Deleting the space SHALL remove every hide indexed from it and no others.

#### Scenario: Hide appears
- **WHEN** alice puts a `feed.commentHide` for bob's comment on her Followers activity in her `interactions` space
- **THEN** after the notification, the comment is no longer counted for anyone, and is listed only for alice and bob, marked as hidden

#### Scenario: Hide deleted
- **WHEN** alice deletes that hide from her `interactions` space
- **THEN** after the notification, the comment is counted and listed as before

#### Scenario: Delete from the other place
- **WHEN** alice deletes the same rkey from her repo, where it isn't
- **THEN** the hide indexed from her `interactions` space stays, and the comment stays hidden

#### Scenario: Interactions space deleted
- **WHEN** alice deletes her `interactions` space
- **THEN** the comments she hid only from that space are no longer hidden, while her hides in her repo stay

#### Scenario: Backfill
- **WHEN** the AppView first syncs an `interactions` space that already holds hides
- **THEN** it indexes those hides along with the space's likes and comments

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
Photos of a non-public activity, and charts stored in a tier space, SHALL be served only to viewers who may see them. The AppView SHALL fetch them from the space with its own credential rather than redirecting the viewer to the PDS.

#### Scenario: Follower views a photo
- **WHEN** an effective follower opens a Followers activity with a photo
- **THEN** the photo loads

#### Scenario: Non-follower requests a photo URL
- **WHEN** a viewer who may not see that activity requests its photo's URL
- **THEN** the AppView doesn't serve the photo

### Requirement: Signed photo URLs are short-lived
A signed URL for a photo of a non-public activity SHALL stop working at most 30 minutes after the AppView issues it, and at least 15 minutes after, so a URL passed on to someone else works only briefly. A viewer who may still see the activity SHALL get a new URL the next time a view containing the photo is loaded. The AppView SHALL NOT let a client cache the photo's response past the moment its URL expires.

#### Scenario: Expiry within bounds
- **WHEN** the AppView signs a photo URL for a viewer at any moment
- **THEN** the URL's expiry is between 15 and 30 minutes later

#### Scenario: Stable within a window
- **WHEN** the AppView signs a viewer's URL for the same photo twice a few seconds apart, within the same 15-minute window
- **THEN** both URLs are the same, so the viewer's device can reuse its cached image

#### Scenario: Expired URL
- **WHEN** a viewer who may see the activity requests the photo with a URL whose expiry has passed
- **THEN** the AppView doesn't serve the photo

#### Scenario: Cache lifetime bounded by the URL
- **WHEN** the AppView serves a photo of a non-public activity
- **THEN** the response may be cached only privately, and for no longer than the time left until the URL expires

### Requirement: Full tracks are not indexed
The AppView SHALL NOT store or serve any `feed.activityTrack` record or its blob, though it syncs the `self` space that holds them.

#### Scenario: Track synced from self
- **WHEN** the AppView syncs an athlete's `self` space holding a `feed.activityTrack`
- **THEN** it stores nothing from that record, and no query or media route returns it

### Requirement: Pulled ops indexed only under a verified commit
Every `listRepoOps` request the AppView makes as a syncer SHALL ask for a repo MAC under a key agreed with the space authority's `#atproto_kem` key, with a fresh key share each time. The AppView SHALL apply a pull's ops and record its revision only after the commit that ends the pull carries a valid MAC for that space and repo.

#### Scenario: Verified pull
- **WHEN** alice writes to her `followers` space and the AppView pulls the new ops with a valid commit MAC
- **THEN** the ops are indexed and the commit's revision becomes the space's last indexed revision

#### Scenario: Commit for another space
- **WHEN** a pull's commit names a different space or repo than the one requested
- **THEN** the AppView indexes none of that pull's ops and keeps the previous revision

#### Scenario: MAC fails twice
- **WHEN** a pull's commit MAC doesn't verify, and still doesn't after the AppView re-resolves alice's DID document and pulls again with a new key share
- **THEN** that space's sync fails and is retried with backoff, none of the pull's ops are indexed, and other spaces keep syncing

#### Scenario: Key rotated
- **WHEN** alice's `#atproto_kem` key changed since the AppView last resolved her DID document
- **THEN** the first MAC fails, the retry with her new key verifies, and the pull is indexed

### Requirement: Synced spaces verified against the commit's set hash
The AppView SHALL keep, for each synced space, the set hash of the authority's repo in that space as upstream `@atproto/space` defines it (an LtHash over `{collection}/{rkey}/{cid}` of every record, whose sha256 is the commit's `data`), over every record in the repo, indexed or not. It SHALL index a pull's ops, or a backfill's records, and record the revision only when the resulting digest equals the `data` of the MAC-verified commit.

#### Scenario: Pull matches
- **WHEN** alice writes to her `followers` space and the pulled ops, folded into the stored set hash, give the commit's `data`
- **THEN** the ops are indexed and the commit's revision and set hash are recorded

#### Scenario: Records outside the indexed collections
- **WHEN** alice's space holds records of a collection the AppView doesn't index from that space
- **THEN** they still count toward the set hash, and are still not indexed

#### Scenario: Backfill matches the head commit
- **WHEN** the AppView backfills a space and the listed records fold to the head commit's `data`
- **THEN** the records are indexed and the head revision and set hash are recorded

#### Scenario: Write during a backfill
- **WHEN** alice writes while the AppView is listing her space, so the listed records don't fold to the head commit it read first
- **THEN** the AppView sees the head revision moved and backfills again, recording a revision only once a listing matches its head

#### Scenario: Backfill never matches
- **WHEN** a backfill's records don't fold to the head commit's `data` and the head revision hasn't moved
- **THEN** the sync fails and is retried with backoff, nothing indexed from the space is removed, and no revision is recorded

#### Scenario: Space synced before set hashes were kept
- **WHEN** a space has a recorded revision but no stored set hash
- **THEN** its next sync is a backfill

### Requirement: Cleared space repos resynced
When a pull's verified commit `data` doesn't match the stored set hash after folding the pulled ops, as after the authority clears their repo with `com.atproto.space.clearRecords`, the AppView SHALL resync the space from a full listing and remove every indexed record the space no longer holds. A commit with no ops and an unchanged `data`, as after the authority's account migrates, SHALL just advance the revision.

#### Scenario: Space cleared
- **WHEN** alice clears her repo in her `followers` space with `clearRecords`
- **THEN** after its next sync the AppView no longer serves any activity, like or other record indexed from that space, and keeps syncing the space

#### Scenario: Cleared then written
- **WHEN** alice clears her space and writes a new activity to it before the AppView pulls
- **THEN** after the next sync the AppView serves only the new activity from that space

#### Scenario: Account migrated
- **WHEN** alice's account moves to another PDS and a pull returns a commit at a new revision with no ops and the same `data`
- **THEN** the AppView records the new revision and removes nothing
