# Spec Delta

## ADDED Requirements

### Requirement: A post card is native on iOS
A native page that lists posts SHALL draw each as the native post card, which SHALL show what the shared card shows, in the same order and words: who reposted it, the author with the post's audience, the text, the embed, Like and Repost on someone else's post, Quote on a listed post, and the counts.

#### Scenario: A shared proposal
- **WHEN** a native page lists bob's Followers post proposing "Wiggly Woods", with 4 up and 1 down votes, which alice may vote on
- **THEN** its card shows bob and Followers, "Name for segment …", "Proposes “Wiggly Woods”", "Open for votes · 4 up · 1 down", and the up and down votes

#### Scenario: A quote of a post the viewer can't see
- **WHEN** a native card shows carol's quote of a post bob deleted
- **THEN** the quoted post's place says "Post unavailable" and opens nothing

### Requirement: The native post card shows each embed
The native post card SHALL show a proposal with its place, name, status, totals and votes; a quoted post one level deep with its proposal named or a link to the post it quotes; "Post unavailable" for a quoted post the viewer can't see; and the shared line for an embed this version can't show.

#### Scenario: A quote of a quote
- **WHEN** a native card shows dave's quote of carol's quote of bob's post
- **THEN** the quoted post shows carol's words and "Quoting another post", which opens bob's post

### Requirement: The native post card's taps do what the shared card's do
On iOS, tapping the post card SHALL open the post page, its author line the author, "Reposted by" the reposter, the place line the segment or dive site, the quoted post its page, and a vote, Like, Repost or Quote SHALL do what the shared card's do. A vote SHALL be taken back by tapping the viewer's own vote again.

#### Scenario: Taking a vote back
- **WHEN** alice, who voted up on bob's proposal, taps the up vote on its native card
- **THEN** her vote is taken back, as from the shared card

### Requirement: Comments are native on iOS
A native page with comments SHALL list them as the shared list does: the count, each thread's comment or "Comment unavailable", its replies indented under it, the viewer's just-sent replies and comments, "View n more replies", hidden comments dimmed with their note, Reply, and a menu with the shared actions in the shared order, Delete and Block author asking first. The next page SHALL be asked for once one of the last five threads shows, and the field SHALL say which comment it answers.

#### Scenario: A hidden comment on the viewer's own post
- **WHEN** alice sees a comment she hid on her own post, on iOS
- **THEN** it is dimmed, says it's hidden, offers no Reply, and its menu offers Unhide
