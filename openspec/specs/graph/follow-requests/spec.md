# graph/follow-requests Specification

## Purpose

Lets an athlete choose to approve followers before a follow takes effect. Defines which follows count, how the athlete approves, declines and removes followers, and what a requester sees while waiting.

## Requirements

### Requirement: Opt-in follow policy
An athlete's follow policy SHALL be `open` or `approve`, stored as `followPolicy` in their public `actor.profile` record. A missing or unrecognised value SHALL be treated as `open`. `getProfile` SHALL return the athlete's policy.

#### Scenario: Default policy
- **WHEN** an athlete whose profile record has no `followPolicy` is fetched with `getProfile`
- **THEN** the view's `followPolicy` is `open`

#### Scenario: Unknown value
- **WHEN** an athlete's profile record has `followPolicy: "friends"`
- **THEN** the AppView treats the athlete's policy as `open`

### Requirement: Effective follows
A follow of an athlete SHALL be effective when the athlete's policy is `open`, or when the athlete has published an `app.activitytracker.graph.followApproval` record whose `subject` is the follower. A follow that isn't effective SHALL be pending. An approval SHALL take effect whether it is indexed before or after the follow it approves. Whatever the policy and approvals, a follow SHALL NOT be effective while either the follower or the athlete blocks the other.

#### Scenario: Open policy
- **WHEN** bob follows alice and alice's policy is `open`
- **THEN** bob's follow is effective without any approval

#### Scenario: Approve policy without approval
- **WHEN** bob follows alice, alice's policy is `approve`, and alice has no approval for bob
- **THEN** bob's follow is pending

#### Scenario: Approval indexed first
- **WHEN** the AppView indexes alice's approval of bob before it indexes bob's follow of alice
- **THEN** bob's follow is effective as soon as it is indexed

#### Scenario: Refollow after unfollow
- **WHEN** alice approved bob, bob unfollows alice, and then follows her again
- **THEN** bob's new follow is effective without another approval

#### Scenario: Block makes a follow ineffective
- **WHEN** bob follows alice, alice's policy is `open`, and either alice blocks bob or bob blocks alice
- **THEN** bob's follow of alice isn't effective while the block holds

### Requirement: Only effective follows count
Every view the AppView derives from follows SHALL use effective follows only. This covers the timeline's authors, `followersCount` and `followsCount`, `getFollowers` and `getFollows`, and whom the viewer follows.

#### Scenario: Pending follower's timeline
- **WHEN** bob's follow of alice is pending and bob fetches his timeline
- **THEN** the timeline contains none of alice's activities

#### Scenario: Pending follower not counted or listed
- **WHEN** bob's follow of alice is pending
- **THEN** alice's `followersCount` doesn't count bob, `getFollowers` for alice doesn't list bob, and `getFollows` for bob doesn't list alice

#### Scenario: Approval makes it count
- **WHEN** alice approves bob's pending follow and the approval is indexed
- **THEN** alice's activities appear in bob's timeline, bob is listed among alice's followers, and her follower count includes him

### Requirement: Requester sees a pending follow
For a signed-in viewer, `getProfile` SHALL report the viewer's follow record in `viewer.following` whether it is effective or pending, and SHALL set `viewer.followRequested` to true only while that follow is pending. The client SHALL label the follow button Requested while a follow is pending, and tapping it SHALL cancel the request by deleting the follow record.

#### Scenario: Following an approve-policy athlete
- **WHEN** bob taps Follow on the profile of alice, whose policy is `approve`
- **THEN** the button reads Requested at once, alice's follower count doesn't change, and after indexing `getProfile` reports `followRequested: true`

#### Scenario: Cancelling a request
- **WHEN** bob taps Requested on alice's profile
- **THEN** bob's follow record is deleted and the button reads Follow

#### Scenario: Request approved
- **WHEN** alice approves bob and bob reloads her profile
- **THEN** the button reads Following and `followRequested` is false

### Requirement: Follow requests inbox
The AppView SHALL serve `app.activitytracker.graph.getFollowRequests` to a signed-in athlete, listing the accounts whose follows of that athlete are pending, newest first, with paging. It SHALL require authentication and SHALL list only follows of the caller.

#### Scenario: Pending requests listed
- **WHEN** alice's policy is `approve` and bob and carol follow her without approvals
- **THEN** `getFollowRequests` called by alice lists bob and carol

#### Scenario: Open policy has no requests
- **WHEN** alice's policy is `open`
- **THEN** `getFollowRequests` called by alice returns an empty list

#### Scenario: Unauthenticated call
- **WHEN** `getFollowRequests` is called without a service-auth token
- **THEN** the AppView answers `401`

### Requirement: Requests reachable from the athlete's profile
When an athlete's policy is `approve`, their own profile SHALL show a Follow requests entry with the number of requests they haven't declined. It SHALL open a screen listing those requests, each with Approve and Decline, and a Declined list.

#### Scenario: Entry shown
- **WHEN** alice, with policy `approve` and two undeclined requests, opens her own profile
- **THEN** she sees Follow requests (2)

#### Scenario: Open policy hides the entry
- **WHEN** an athlete with policy `open` opens their own profile
- **THEN** no Follow requests entry is shown

### Requirement: Approving a follower
Approving a follower SHALL write a public `app.activitytracker.graph.followApproval` record to the athlete's repo with the follower's DID as `subject` and as its rkey. Approving SHALL delete any decline of that follower. Approving the same follower twice SHALL leave one approval.

#### Scenario: Approve a request
- **WHEN** alice taps Approve on bob's request
- **THEN** alice's repo holds `app.activitytracker.graph.followApproval/<bob's DID>` with `subject` bob, and bob disappears from her requests

#### Scenario: Approve from Declined
- **WHEN** alice taps Approve on bob in her Declined list
- **THEN** the approval is written, the decline record is deleted, and bob is no longer in Declined

### Requirement: Declining privately
Declining a request SHALL write an `app.activitytracker.graph.followDecline` record, with the follower's DID as `subject` and rkey, to the athlete's `self` tier space and nowhere else, creating that space if needed. Nothing public SHALL record a decline. The declined follow SHALL stay pending.

#### Scenario: Decline a request
- **WHEN** alice taps Decline on bob's request
- **THEN** a `followDecline` for bob exists in alice's `self` space, her repo has no record about bob, and bob moves from her requests to Declined

#### Scenario: Declined requester's view
- **WHEN** alice has declined bob and bob opens alice's profile
- **THEN** bob's button still reads Requested, and nothing he can read says he was declined

#### Scenario: Declined follower refollows
- **WHEN** alice declined bob, and bob cancels his request and follows again
- **THEN** bob stays in alice's Declined list and doesn't reappear in her requests

### Requirement: Declined list
The Declined list SHALL show the accounts the athlete has declined that still have a pending follow of the athlete. A declined account whose follow is gone SHALL not be shown.

#### Scenario: Requester cancelled
- **WHEN** alice declined bob and bob then deleted his follow record
- **THEN** bob isn't in alice's Declined list

### Requirement: Turning approval on keeps current followers
When an athlete turns approval on, the client SHALL write approvals for every account that currently follows them before it saves `followPolicy: approve`. If writing the approvals fails, the policy SHALL stay `open`.

#### Scenario: Existing followers kept
- **WHEN** alice has 300 followers and turns on Approve followers
- **THEN** alice's repo holds an approval for each of them, then her profile has `followPolicy: approve`, and none of them becomes pending

#### Scenario: Approval batch fails
- **WHEN** alice turns on Approve followers and writing the approvals fails part-way
- **THEN** her policy stays `open`, the athlete sees an error, and retrying writes the remaining approvals

### Requirement: Turning approval off
When an athlete turns approval off, their policy SHALL become `open`, so every follow of them is effective. Their approvals and declines SHALL stay in place.

#### Scenario: Pending become followers
- **WHEN** alice has pending requests from bob and carol and turns approval off
- **THEN** bob and carol are effective followers once her profile is indexed

#### Scenario: Turning it back on
- **WHEN** alice turns approval off and then on again
- **THEN** followers she had approved stay effective and accounts she had declined stay in Declined

### Requirement: Removing a follower
With policy `approve`, an athlete SHALL be able to remove a follower from their own followers list, which deletes the athlete's approval of that follower so the follow becomes pending again. With policy `open`, the followers list SHALL offer no Remove action.

#### Scenario: Remove a follower
- **WHEN** alice, with policy `approve`, removes bob from her followers
- **THEN** her approval of bob is deleted, bob is no longer an effective follower, and bob appears in her requests

#### Scenario: Open policy
- **WHEN** an athlete with policy `open` views their own followers list
- **THEN** no Remove action is offered

### Requirement: Followers visibility
`followers` SHALL be a visibility value, meaning the athlete's effective followers. Activity charts (streams) with visibility `followers` SHALL be served to the athlete and their effective followers only. Every visibility picker SHALL offer Followers and, while Followers is chosen and the athlete's policy is `open`, SHALL note that anyone who follows them can see it.

#### Scenario: Effective follower sees Followers charts
- **WHEN** bob is an effective follower of alice and fetches streams for her activity with visibility `followers`
- **THEN** the AppView returns the streams

#### Scenario: Pending follower refused charts
- **WHEN** bob's follow of alice is pending and he fetches streams for her activity with visibility `followers`
- **THEN** the AppView answers as if the streams didn't exist

#### Scenario: Picker note with open policy
- **WHEN** an athlete with policy `open` chooses Followers in a visibility picker
- **THEN** the picker notes that anyone who follows them can see it

#### Scenario: No note when approving followers
- **WHEN** an athlete with policy `approve` chooses Followers in a visibility picker
- **THEN** no such note is shown

### Requirement: Viewer sees whether the athlete follows them
For a signed-in viewer, `getProfile` SHALL report the athlete's follow record of the viewer in `viewer.followedBy` when that follow is effective. It SHALL be absent when the athlete doesn't follow the viewer, when the athlete's follow is pending the viewer's approval, and when the viewer is signed out.

#### Scenario: Athlete follows an open-policy viewer
- **WHEN** alice follows bob, bob's policy is `open`, and bob calls `getProfile` for alice
- **THEN** `viewer.followedBy` is the URI of alice's follow of bob

#### Scenario: Follow pending the viewer's approval
- **WHEN** alice follows bob, bob's policy is `approve`, bob hasn't approved alice, and bob calls `getProfile` for alice
- **THEN** `viewer.followedBy` is absent

#### Scenario: Viewer approves the follow
- **WHEN** bob, with policy `approve`, approves alice's follow and then calls `getProfile` for alice
- **THEN** `viewer.followedBy` is the URI of alice's follow of bob

#### Scenario: No follow back
- **WHEN** bob follows alice, alice doesn't follow bob, and bob calls `getProfile` for alice
- **THEN** `viewer.following` is bob's follow and `viewer.followedBy` is absent

#### Scenario: Signed out
- **WHEN** a signed-out client calls `getProfile` for alice
- **THEN** the view has no `viewer.followedBy`
