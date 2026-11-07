# Spec Delta

## MODIFIED Requirements

### Requirement: A post is served on its own
`feed.getPost` SHALL return one post's view, by URI, under the same rules as every other read of posts. A post the viewer may not see, one that isn't indexed, one whose proposal may not be shown to the viewer, and a quote the viewer isn't shown SHALL each be `NotFound`, with no way to tell them apart.

#### Scenario: Follower opens a Followers post
- **WHEN** bob, alice's effective follower, asks for her Followers post
- **THEN** he gets its view, with its proposal as it stands now and its counts

#### Scenario: Outside the audience
- **WHEN** carol, who doesn't follow alice, asks for that post
- **THEN** the answer is `NotFound`

#### Scenario: Proposal withdrawn
- **WHEN** alice deletes the proposal her post shares
- **THEN** asking for the post is `NotFound`

#### Scenario: Quote outside the quoted post's audience
- **WHEN** carol follows bob but not alice, and asks for bob's quote of alice's Followers post
- **THEN** the answer is `NotFound`

## ADDED Requirements

### Requirement: The post page lists quotes
The post page SHALL show the post's quote count and offer Quote as the card does. Tapping the count SHALL list the quotes of the post the viewer is shown, from `feed.getQuotes`, as post cards, newest first, a page at a time, in the content-loading pattern, with an empty state when there are none.

#### Scenario: Quotes
- **WHEN** bob taps the quote count on alice's Followers post
- **THEN** he sees the quotes of it he may see, as cards, newest first

#### Scenario: Own quote listed at once
- **WHEN** bob quotes alice's post from its page and then taps the quote count
- **THEN** his quote is listed first, even before the AppView has indexed it
