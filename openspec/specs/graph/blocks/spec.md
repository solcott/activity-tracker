# graph/blocks Specification

## Purpose

Lets an athlete block an account so that neither can see or interact with the other in this app. Defines where blocks are stored, how Bluesky blocks are honored, and what a block hides in both directions.

## Requirements

### Requirement: A block is stored privately
Blocking an account SHALL write an `app.activitytracker.graph.block` record, with the account's DID as `subject` and as its rkey, to the athlete's `interactions` space and nowhere else, creating that space if needed. Nothing public SHALL record the block. Blocking the same account twice SHALL leave one block.

#### Scenario: Block written to the interactions space
- **WHEN** alice blocks bob
- **THEN** alice's `interactions` space holds a `graph.block` record with rkey and `subject` bob's DID, and alice's repo holds no record naming bob as blocked

#### Scenario: Second block of the same account
- **WHEN** alice blocks bob and then blocks him again
- **THEN** her `interactions` space holds one `graph.block` for bob

#### Scenario: Only the athlete and the AppView read it
- **WHEN** bob or any other user other than alice and the AppView asks to read alice's `interactions` space
- **THEN** the read is refused, so nobody else can learn of the block

### Requirement: Block refused without the space scope
A client SHALL refuse to block or unblock, before writing anything, when the session's `space:` scope doesn't cover `graph.block`. It SHALL report the refusal as needing to sign in again.

#### Scenario: Session from before blocks existed
- **WHEN** alice's session was signed in before `graph.block` was declared in the athlete space, and she taps Block on bob's profile
- **THEN** nothing is written and the screen tells her to sign in again

### Requirement: Unblocking
Unblocking an account SHALL delete the athlete's `graph.block` record for it. A missing record or a missing `interactions` space SHALL count as done. Once the AppView has indexed the deletion, the block SHALL no longer apply.

#### Scenario: Unblock
- **WHEN** alice unblocks bob and the AppView has indexed the deletion
- **THEN** alice and bob see each other again as if the block had never been

### Requirement: Follows outlast a block
A block SHALL NOT delete either athlete's follow, follow approval or follow decline records. While the block holds, those follows SHALL NOT be effective. After an unblock they SHALL count again under the usual rules.

#### Scenario: Follow returns after unblock
- **WHEN** bob followed alice under an `open` policy, alice blocks bob, and later unblocks him
- **THEN** bob's follow of alice is effective again without a new follow record

### Requirement: Bluesky blocks are honored
The AppView SHALL treat an `app.bsky.graph.block` record in an athlete's repo as a block of its `subject` by that athlete, exactly as if it were an `app.activitytracker.graph.block`. Deleting the record SHALL remove that block. The AppView SHALL NOT honor Bluesky mutes, block lists or list blocks.

#### Scenario: Bluesky block applies here
- **WHEN** alice, an athlete, blocked bob on Bluesky with an `app.bsky.graph.block` record
- **THEN** the AppView applies a block between alice and bob

#### Scenario: Bluesky block removed
- **WHEN** alice deletes that `app.bsky.graph.block` record and the AppView has indexed the deletion
- **THEN** the block no longer applies, unless alice also has an `app.activitytracker.graph.block` of bob

#### Scenario: Block list ignored
- **WHEN** alice subscribes to a Bluesky block list that includes bob
- **THEN** no block applies between alice and bob in this app

### Requirement: Only athletes' Bluesky blocks are kept
The AppView SHALL keep an `app.bsky.graph.block` only when its author is an athlete, meaning an account it has indexed an `app.activitytracker.*` record from (in their repo or a space). The subject SHALL NOT need to be an athlete. It SHALL drop every other Bluesky block it receives without storing anything, the author included.

#### Scenario: Non-athlete's block dropped
- **WHEN** Jetstream delivers an `app.bsky.graph.block` by an account the AppView has indexed no app record from
- **THEN** the AppView stores nothing from it, and doesn't start tracking that account

#### Scenario: Block of a non-athlete kept
- **WHEN** alice, an athlete, blocks carol on Bluesky and carol has never used the app
- **THEN** the AppView keeps the block, so it applies once carol joins

### Requirement: Existing Bluesky blocks are backfilled
After the AppView first indexes a record from an athlete, and once for every athlete it already knew when this capability shipped, it SHALL fetch every `app.bsky.graph.block` record in that athlete's repo and index them. A failed backfill SHALL be retried later and SHALL NOT hold up indexing.

#### Scenario: Blocks made before joining
- **WHEN** alice blocked bob on Bluesky last year and then creates her athlete profile today
- **THEN** once the AppView has indexed her profile and backfilled, the block between alice and bob applies

#### Scenario: Athletes from before blocks shipped
- **WHEN** the AppView starts with this capability for the first time and alice was already indexed
- **THEN** it backfills alice's Bluesky blocks too

#### Scenario: Backfill failure
- **WHEN** fetching alice's Bluesky blocks fails because her PDS is unreachable
- **THEN** alice's profile is still indexed, and the backfill is tried again later

### Requirement: A block works both ways
While either of two athletes blocks the other, through either kind of record, the AppView SHALL treat each of them as unable to see anything of the other's except what the athlete who blocks needs to manage the block. Every requirement in this capability SHALL apply whichever of the two made the block.

#### Scenario: Blocked athlete can't see the blocker
- **WHEN** alice blocks bob
- **THEN** bob can't see alice's activities, likes or comments in this app

#### Scenario: Blocker doesn't see the blocked
- **WHEN** alice blocks bob
- **THEN** alice can't see bob's activities, likes or comments in this app

### Requirement: Blocked accounts' activities are hidden
The AppView SHALL NOT return an activity to a viewer in a block with the activity's athlete, at any visibility, Public included. This SHALL hold for the timeline, an athlete's activities, a single activity and its streams and media. A single activity SHALL answer as not found.

#### Scenario: Timeline
- **WHEN** alice blocks bob and bob follows alice
- **THEN** bob's timeline has none of alice's activities

#### Scenario: Single activity
- **WHEN** alice blocks bob and bob asks the AppView for one of alice's Public activities by URI
- **THEN** the AppView answers as it does for an activity that doesn't exist

#### Scenario: Photos of a non-public activity
- **WHEN** alice blocks bob and bob asks the AppView for a photo of one of alice's Followers activities, with a URL signed for him before the block
- **THEN** the AppView doesn't serve it

#### Scenario: Photos of a Public activity
- **WHEN** alice blocks bob and bob loads a photo of one of alice's Public activities by its URL
- **THEN** the AppView may still redirect him to it, because photo URLs carry no viewer and the photo is as public as the record (`Public records stay public`), but no activity view the AppView serves him links to it

### Requirement: Blocked accounts' likes and comments are hidden
The AppView SHALL NOT list or count, for a viewer, a like or comment whose giver is in a block with the viewer. It SHALL NOT list or count a like or comment for anyone when its giver is in a block with the athlete whose activity it is on. A thread whose top-level comment is hidden this way SHALL follow `Unavailable top-level comment`.

#### Scenario: Blocked commenter on the blocker's activity
- **WHEN** bob commented on alice's Public activity and then alice blocks bob
- **THEN** bob's comment is neither listed nor counted for anyone

#### Scenario: Comments on a third athlete's activity
- **WHEN** alice blocks bob, and both commented on carol's Public activity
- **THEN** alice isn't shown bob's comment and bob isn't shown alice's, while carol and others see both, and each count matches what that viewer is listed

#### Scenario: Likes
- **WHEN** alice blocks bob and bob liked carol's activity
- **THEN** alice's like count and likes list on it leave bob out

### Requirement: Interactions written around a block are hidden while it holds
A like, comment or reply between two athletes in a block SHALL be hidden as `Blocked accounts' likes and comments are hidden` requires, whether it was written before the block or during it, including one written straight to a PDS. After an unblock, it SHALL be shown again under the usual rules.

#### Scenario: Comment written despite the block
- **WHEN** alice blocks bob, and bob writes a `feed.comment` on alice's activity straight to his PDS
- **THEN** the AppView doesn't list or count it for anyone while the block holds

#### Scenario: Reply to the blocker on a third athlete's activity
- **WHEN** alice blocks bob, and bob replies to alice's comment on carol's activity
- **THEN** alice isn't shown the reply while the block holds

#### Scenario: Unblock restores
- **WHEN** alice unblocks bob and the AppView has indexed the deletion
- **THEN** bob's earlier comments on alice's activities are listed and counted again for viewers who may see them

### Requirement: Blocked profiles answer as not found
`getProfile` SHALL answer as not found to a viewer whom the athlete blocks. To the athlete who blocks, it SHALL return the blocked account's profile with `viewer.blocking` set to the URI of their block, and the client SHALL show Unblock in place of Follow and show none of that account's activities.

#### Scenario: Blocked viewer
- **WHEN** alice blocks bob and bob opens alice's profile
- **THEN** the AppView answers as for a profile that doesn't exist, and nothing says bob is blocked

#### Scenario: Blocker opens the blocked profile
- **WHEN** alice blocks bob and opens bob's profile from her Blocked accounts list
- **THEN** she sees his name and avatar, an Unblock button and no activities

#### Scenario: Blocked only on Bluesky
- **WHEN** alice's only block of bob is an `app.bsky.graph.block` and she opens his profile
- **THEN** she sees that he is blocked on Bluesky and no Unblock, in the button's place or the menu, because the app never writes `app.bsky.*` records

### Requirement: A block is silent
No response the AppView gives a blocked account SHALL say it is blocked or differ from what it would get for an account or record that doesn't exist. `viewer.blocking` SHALL be set only for the viewer's own blocks.

#### Scenario: No blocked-by field
- **WHEN** alice blocks bob and bob fetches anything that names alice
- **THEN** no field in the response says alice blocks bob

### Requirement: Blocked accounts leave lists and search
The AppView SHALL leave an account in a block with the viewer out of the viewer's search results and out of every followers, follows and follow-requests list it returns to the viewer. Counts SHALL leave out follows that a block makes ineffective.

#### Scenario: Search
- **WHEN** alice blocks bob and bob searches for alice's handle
- **THEN** alice isn't in his results

#### Scenario: Someone else's followers list
- **WHEN** alice blocks bob and alice views carol's followers, which include bob
- **THEN** bob isn't listed for alice

### Requirement: Blocked tier access is denied
A user in a block with an athlete SHALL be denied `read` on every one of that athlete's spaces, whatever the follow graph says, as `Read access by tier` defines.

#### Scenario: Blocked follower reads followers tier
- **WHEN** bob's follow of alice is otherwise effective and alice blocks bob
- **THEN** a `read` check for bob on alice's `followers` space answers `authorized: false`

### Requirement: Blocking from a profile or comment
Clients SHALL offer Block on any other athlete's profile and on any comment written by someone else, and Unblock on a profile the viewer blocks. Blocking SHALL ask for confirmation first, saying the account won't be told. Clients SHALL NOT offer either on the viewer's own profile or comments.

#### Scenario: Block from a comment
- **WHEN** alice opens the menu on bob's comment and chooses Block, then confirms
- **THEN** alice's client writes the block

#### Scenario: Own profile
- **WHEN** alice views her own profile
- **THEN** no Block action is offered

### Requirement: A block shows at once
Once the athlete confirms a block, the client SHALL hide the blocked account's activities, likes and comments, and show Unblock on its profile, at once and without waiting for the AppView. This SHALL still hold when a screen reloads before the AppView has caught up. Unblocking SHALL undo this the same way. A failed write SHALL restore the previous state and report the failure.

#### Scenario: Comments vanish at once
- **WHEN** alice blocks bob from his comment on carol's activity
- **THEN** bob's comments leave the list and the count drops at once

#### Scenario: Write fails
- **WHEN** alice blocks bob and writing the block fails
- **THEN** bob's comments return, the profile shows Block again, and the screen reports the failure

#### Scenario: Reload before the AppView catches up
- **WHEN** alice blocks bob and reopens carol's activity before the AppView has indexed the block
- **THEN** bob's comments are still hidden

### Requirement: Blocked accounts list
Settings SHALL offer a Blocked accounts screen listing every account the athlete blocks, newest first, from `app.activitytracker.graph.getBlocks`, which the AppView SHALL serve only to the authenticated caller about their own blocks. Each entry SHALL offer Unblock, except a block held only by a Bluesky record.

#### Scenario: Listing
- **WHEN** alice blocks bob in the app and carol on Bluesky
- **THEN** her Blocked accounts screen lists both, newest first

#### Scenario: Bluesky-only block
- **WHEN** alice's only block of carol is an `app.bsky.graph.block`
- **THEN** carol's entry says she is blocked on Bluesky, offers no Unblock, and the app writes no `app.bsky.*` record

#### Scenario: Someone else's blocks
- **WHEN** bob calls `getBlocks`
- **THEN** he gets only his own blocks, and never alice's

### Requirement: Public records stay public
The capability SHALL NOT be described to athletes as hiding their Public activities from the blocked account outside this app. The block confirmation SHALL say that Public activities can still be seen outside the app.

#### Scenario: Confirmation wording
- **WHEN** alice is asked to confirm blocking bob
- **THEN** the confirmation says bob won't be told, and that her Public activities can still be seen outside the app
