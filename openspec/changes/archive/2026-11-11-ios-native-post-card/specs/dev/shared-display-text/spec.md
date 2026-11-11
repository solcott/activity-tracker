# Spec Delta

## ADDED Requirements

### Requirement: A post card reads the same in both UIs
A post card's reposted-by line, audience, proposal place, name, status and totals, the quoted post's header, "Post unavailable", the counts, which of Like, Repost, Quote, the votes and the menu it offers, and what a vote tap sends SHALL be chosen by shared code that both UIs ask.

#### Scenario: Reposted by the viewer
- **WHEN** alice's timeline shows carol's post through alice's own repost
- **THEN** both UIs head it "Reposted by you"

#### Scenario: Counts
- **WHEN** a post has 2 likes, 1 comment, no reposts and 1 quote
- **THEN** both UIs show "2 likes · 1 comment · 0 reposts · 1 quote"

### Requirement: A comment's actions are decided once
Which actions a comment offers (Reply, Delete, Hide or Unhide, Mute author, Block author, Report), in what order, and the words of its hidden note and of the delete and block confirmations SHALL be decided by shared code that both UIs ask.

#### Scenario: Someone else's comment on someone else's post
- **WHEN** alice sees carol's comment on bob's post with a labeler configured
- **THEN** both UIs decide Reply, Mute author, Block author and Report, and no Delete or Hide
