# feed/quote-posts Specification

## Purpose

Defines quote posts: how an athlete adds their own words around someone else's post (or their own), where the quote is stored, who is shown it and the post it quotes, and how a post's quotes are counted and listed.

## Requirements

### Requirement: Quoting a post
Clients SHALL offer Quote beside Repost on every post the viewer can see, their own included, on the post card and the post page. Quote SHALL open a sheet with a text field and an audience picker, and SHALL offer no other way to start a quote.

#### Scenario: Quote from the card
- **WHEN** bob taps Quote on alice's post in his timeline
- **THEN** a sheet opens with an empty text field and an audience picker

#### Scenario: Own post
- **WHEN** alice taps Quote on her own post
- **THEN** the quote sheet opens as it does for anyone else's post

### Requirement: A quote is a post that names another post
A quote SHALL be a `feed.post` whose embed is a quote naming another `feed.post` by strongRef, under a new record key. It SHALL be stored by its audience as any post is. The AppView SHALL ignore a quote whose subject isn't a `feed.post`.

#### Scenario: Followers quote
- **WHEN** bob quotes alice's Followers post for Followers
- **THEN** a `feed.post` quoting alice's post is written to bob's `followers` space, and his repo gains no post

#### Scenario: Quote of an activity
- **WHEN** bob's repo holds a `feed.post` whose quote names one of alice's activities
- **THEN** the AppView ignores it

### Requirement: A quote's text is required
The quote sheet SHALL offer Post only once the text, trimmed, isn't empty. The AppView SHALL ignore a quote whose text is missing or empty after trimming, and text over the post limit.

#### Scenario: Blank text
- **WHEN** bob has typed only spaces in the quote sheet
- **THEN** Post isn't offered

#### Scenario: Quote with no text
- **WHEN** a `feed.post` quoting alice's post has no text
- **THEN** the AppView ignores it

### Requirement: A quote's audience is no wider than the quoted post's
The quote sheet SHALL offer only audiences no wider than the quoted post's visibility as the quoter's view showed it, where Public is wider than Followers and Followers wider than Mutuals, and SHALL start on the quoted post's visibility. A quote's audience SHALL NOT be changed once written.

#### Scenario: Followers post
- **WHEN** bob quotes alice's Followers post
- **THEN** the sheet starts on Followers and offers Followers and Mutuals, not Public

#### Scenario: Public post
- **WHEN** bob quotes alice's Public post
- **THEN** the sheet starts on Public and offers Public, Followers and Mutuals

### Requirement: A quote shows at once
Posting a quote SHALL add it to the top of the quoter's timeline and profile Posts, and raise the quoted post's quote count by one, at once, before the AppView has indexed it. A quote that fails to queue SHALL leave nothing changed and say why.

#### Scenario: Posted
- **WHEN** bob posts a quote of alice's post
- **THEN** his quote is first in his timeline and on his profile's Posts, and alice's post shows its quote count one higher

#### Scenario: Without the space scope
- **WHEN** bob's session can't write `feed.post` to his `followers` space and he posts a Followers quote
- **THEN** the quote is refused before anything changes, with the same explanation a like gets

### Requirement: An athlete deletes their own quote
The menu on an athlete's own quote, on the card and the post page, SHALL offer Delete, which SHALL ask to confirm. Deleting SHALL remove the quote from every list at once and delete the record wherever it is stored. The menu SHALL offer no Delete on other athletes' quotes or on proposal posts.

#### Scenario: Delete
- **WHEN** bob deletes his Followers quote and confirms
- **THEN** it leaves his timeline and profile at once, and the record is deleted from his `followers` space

#### Scenario: Someone else's quote
- **WHEN** alice opens the menu on bob's quote of her post
- **THEN** it offers no Delete

### Requirement: Who is shown a quote
A quote SHALL be served to a viewer only while its tier admits them, as any post is, and, while the quoted post is served to anyone, only while the viewer may also see the quoted post. A quote SHALL never widen the quoted post's audience.

#### Scenario: Outside the quoted post's audience
- **WHEN** carol follows bob but not alice, and bob quotes alice's Followers post for Followers
- **THEN** carol isn't shown the quote anywhere, and its quote count, likes, comments and reposts aren't counted or listed for her

#### Scenario: Narrowed after quoting
- **WHEN** bob quoted alice's Public post publicly, and alice moves the post to Mutuals
- **THEN** only viewers who are alice's mutuals and may see bob's quote are shown it

#### Scenario: Blocked author
- **WHEN** carol blocks alice and follows bob, who quoted alice's Public post
- **THEN** carol isn't shown bob's quote

#### Scenario: Quote of a quote outside the first post's audience
- **WHEN** dave follows carol and bob but not alice, bob quoted alice's Followers post, and carol quoted bob's quote
- **THEN** dave is shown neither quote

### Requirement: Chains of quotes are limited
A chain of quotes SHALL be followed from a quote, through the post it quotes, to the first post that isn't a quote. A quote more than 8 quotes from the end of its chain SHALL NOT be served to anyone.

#### Scenario: Ninth level
- **WHEN** a chain holds a proposal post, a quote of it, a quote of that quote, and so on, nine quotes in all
- **THEN** that quote, and any quote of it, is served to no one

### Requirement: The quoted post in a quote
A quote's view SHALL show the quoted post one level deep: its author, text, visibility and creation time, with its proposal as it stands now when it shares one, or a link to the third post when it is itself a quote. It SHALL show "Post unavailable" in place of the quoted post once that post is served to no one, or once the quoter may no longer see it, and SHALL then reveal nothing about it.

#### Scenario: Quoted proposal
- **WHEN** bob's timeline shows his quote of alice's proposal post
- **THEN** the quote shows alice, her text and her proposal with its current name and status

#### Scenario: Quote of a quote
- **WHEN** carol quotes bob's quote of alice's post
- **THEN** carol's quote shows bob's quote with a link to alice's post in place of its embed

#### Scenario: Quoted post deleted
- **WHEN** alice deletes a post bob quoted
- **THEN** bob's quote is still served to those who may see it, showing "Post unavailable" in place of alice's post

#### Scenario: Quoter loses access
- **WHEN** bob quoted alice's Followers post, and alice blocks bob
- **THEN** bob's quote shows "Post unavailable" to every viewer still shown it

### Requirement: Tapping the quoted post opens it
Tapping the quoted post inside a quote SHALL open the quoted post's page, and tapping a quote-of-a-quote link SHALL open the third post's page. "Post unavailable" SHALL open nothing.

#### Scenario: Open the quoted post
- **WHEN** bob taps alice's post inside his quote
- **THEN** alice's post page opens

### Requirement: Quotes are counted and listed
A post's quote count SHALL be the number of quotes of it the viewer is shown, its author's own included. `feed.getQuotes` SHALL list those quotes as posts, newest first by feed position, with cursor paging. A viewer who may not see the post, and any subject that isn't a post, SHALL get `NotFound`.

#### Scenario: Counted for the viewer
- **WHEN** bob and carol quote alice's Public post, bob for Followers, and dave follows carol but not bob
- **THEN** dave sees a quote count of one, and `feed.getQuotes` lists only carol's quote for him

#### Scenario: Outside the audience
- **WHEN** a viewer who may not see alice's Followers post asks `feed.getQuotes` for it
- **THEN** the answer is `NotFound`

### Requirement: A quote is a post like any other
A quote SHALL be in timelines and on its author's profile as their other posts are, and SHALL take likes, comments, reposts and quotes, with blocks, mutes, labels and reports applying, under the rules for posts.

#### Scenario: Liked and reposted
- **WHEN** dave, bob's effective follower who may see alice's post, likes and reposts bob's quote of it
- **THEN** the quote's like and repost counts go up as they would on any post

#### Scenario: Quote taken down
- **WHEN** the labeler labels bob's quote `!takedown`
- **THEN** the quote is served to no one, and alice's post is unaffected
