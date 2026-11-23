# Spec Delta

## Purpose

Lets the riders who know a segment keep its trail details true: regulars suggest a value for one detail, riders confirm or vote, and a suggestion that enough riders agree with becomes the value every view serves, with where each value came from.

## ADDED Requirements

### Requirement: A trail detail suggestion is a public record
A suggestion SHALL be a `feed.trailDetailSuggestion` in its author's repo, naming a segment by URI, one detail and a value in that detail's vocabulary as `segments/trail-attributes` defines it. The AppView SHALL ignore one in any space, one naming a segment it hasn't indexed, and one whose value is outside the detail's vocabulary.

#### Scenario: Suggestion written
- **WHEN** alice suggests "No dogs" for Wiggly Woods
- **THEN** her repo gains a `feed.trailDetailSuggestion` naming the segment, the detail `dog` and the value `no`

#### Scenario: Value outside the vocabulary
- **WHEN** a suggestion for `dog` holds the value `sometimes`
- **THEN** the AppView ignores it

#### Scenario: Suggestion in a space
- **WHEN** a `feed.trailDetailSuggestion` appears in an athlete's `interactions` space
- **THEN** the AppView ignores it

### Requirement: Riders may suggest every detail but official and measured ones
A suggestion SHALL name one of: surface (one surface for the whole segment), smoothness, hiking, MTB, MTB uphill, IMBA and piste difficulty, piste grooming, visibility, width (singletrack, doubletrack or road width), lit, dogs, horses, wheelchair, and season (open dates, seasons, or seasonal). The AppView SHALL ignore a suggestion for trail class, typical grade, operator or website.

#### Scenario: Website
- **WHEN** an eligible athlete writes a suggestion for `website`
- **THEN** the AppView ignores it

#### Scenario: Width
- **WHEN** alice suggests doubletrack for a segment's width
- **THEN** once adopted, the segment's Width row reads "Doubletrack"

### Requirement: Only regulars may suggest
The AppView SHALL accept a suggestion only if, when it's indexed, the segment is `active` and not a lift, and its author has efforts on it on at least a configured number of distinct local dates, 5 by default, counting efforts of every visibility within the sport group's speed cap. It SHALL keep an accepted suggestion when its author later loses efforts.

#### Scenario: Five days of efforts
- **WHEN** alice has efforts on a segment on 5 dates, two from Only me activities, and suggests a value
- **THEN** the AppView accepts the suggestion

#### Scenario: Athlete's own segment
- **WHEN** an eligible athlete suggests a surface for an active segment in another athlete's repo
- **THEN** the AppView accepts it as for any segment

#### Scenario: Lift
- **WHEN** an athlete with many lift efforts suggests a detail for a lift
- **THEN** the AppView ignores it

### Requirement: A suggestion must offer a new value
The AppView SHALL ignore a suggestion whose value equals the detail's value in effect or the value of another open suggestion on the same segment and detail. It SHALL ignore one from an athlete who already has an open suggestion on that segment and detail. Deleting an open suggestion SHALL withdraw it and its votes.

#### Scenario: Same as the value in effect
- **WHEN** a segment's dogs value is "Dogs on leash" and alice suggests "Dogs on leash"
- **THEN** the AppView ignores the suggestion

#### Scenario: Second open suggestion
- **WHEN** alice has an open dogs suggestion on a segment and writes another for its dogs
- **THEN** the AppView ignores the second one

#### Scenario: Another detail
- **WHEN** alice has an open dogs suggestion on a segment and suggests its surface
- **THEN** the AppView accepts the surface suggestion

### Requirement: Editing a suggestion clears its votes
When an author rewrites their open suggestion with a different value, the AppView SHALL take the new value, drop every vote cast before the edit and restart its time open, after the value checks of a new suggestion. An edit that fails them SHALL leave the suggestion as it was. A rewrite of a closed suggestion SHALL be ignored.

#### Scenario: Edited
- **WHEN** alice's open suggestion "No dogs" has 3 up votes and she edits it to "Dogs allowed"
- **THEN** it's served as "Dogs allowed" with no votes, opened at the time of the edit

### Requirement: Votes and confirmations are private records
A vote SHALL be a `feed.trailDetailVote` in the voter's `interactions` space. It is either up or down on a suggestion, or "still right" or "changed" on a segment's detail as it was in effect when the vote was cast. The AppView SHALL ignore one in a repo or tier space, count only a voter's latest on each subject, and never serve who voted or how, except a viewer's own to them.

#### Scenario: Vote written
- **WHEN** bob agrees with a suggestion
- **THEN** his `interactions` space gains a `feed.trailDetailVote` naming the suggestion, and his repo gains nothing

#### Scenario: Confirmation written
- **WHEN** bob taps "Still right" on a segment's dogs value "Dogs on leash"
- **THEN** his `interactions` space gains a `feed.trailDetailVote` naming the segment, the detail `dog`, the value `leashed` and "still right"

### Requirement: Only riders' votes count
A vote SHALL count only while its voter has at least one effort on the segment, of any visibility. An author's vote on their own suggestion SHALL NOT count. A vote on a closed suggestion SHALL NOT change its totals. A confirmation SHALL count only while the value it names is the detail's value in effect.

#### Scenario: Voter without efforts
- **WHEN** carol, with no efforts on a segment, agrees with a suggestion for it
- **THEN** the suggestion's totals don't change

#### Scenario: Confirmation of a replaced value
- **WHEN** bob confirmed "Dogs on leash" and a "No dogs" suggestion is later adopted
- **THEN** bob's confirmation no longer counts toward the segment's dogs confirmations

### Requirement: A suggestion is adopted once enough riders agree
The AppView SHALL adopt an open suggestion once it has been open at least 7 days, its up votes reach the larger of 3 and 10% (rounded up) of the distinct athletes with efforts on the segment dated in the last 365 days, and at least two-thirds of its counted votes are up. It SHALL check at least hourly, only for `active` segments. Each threshold SHALL be configurable.

#### Scenario: Quiet segment
- **WHEN** a segment has 12 riders in the last year and a suggestion open 8 days has 3 up and 1 down
- **THEN** the AppView adopts it

#### Scenario: Too soon
- **WHEN** a suggestion open 3 days has 20 up and no down votes
- **THEN** the AppView doesn't adopt it before it has been open 7 days

### Requirement: Adopting closes the detail's other suggestions
When the AppView adopts a suggestion, it SHALL close every other open suggestion on the same segment and detail as superseded, and SHALL ignore new suggestions on that detail for a configured period, 30 days by default. When several qualify in one check, it SHALL adopt the one with the highest up-minus-down score, then the one opened first.

#### Scenario: Cooldown
- **WHEN** a segment's dogs value was adopted 10 days ago and an eligible athlete suggests another
- **THEN** the AppView ignores the suggestion

#### Scenario: Other details unaffected
- **WHEN** a segment's dogs value was adopted 10 days ago and an eligible athlete suggests its surface
- **THEN** the AppView accepts it

### Requirement: Suggestions are rejected or expire
The AppView SHALL close an open suggestion as rejected once its up-minus-down score reaches −5, and as expired once open 90 days without being adopted. Both values SHALL be configurable.

#### Scenario: Voted down
- **WHEN** a suggestion has 1 up and 6 down votes
- **THEN** it's closed as rejected and no longer listed

### Requirement: Adopted values become the effective details
A segment's effective details SHALL be, for each detail, its latest adopted value still in effect, else the importer's value, if the segment is in the importer's repo. An adopted value SHALL stay in effect when the importer later changes that detail on the record. Every view that serves a segment's `trail` SHALL serve its effective details.

#### Scenario: Community value on an importer segment
- **WHEN** an importer segment's record says `dog: leashed` and a "No dogs" suggestion was adopted
- **THEN** its view's `trail` holds `dog: no`

#### Scenario: Re-import after adoption
- **WHEN** a segment adopted "No dogs" and the importer's `refine` then rewrites its record with `dog: yes`
- **THEN** its view keeps `dog: no`

#### Scenario: Athlete's segment
- **WHEN** an athlete's segment has no importer details and adopted a 100% `gravel` surface
- **THEN** its view's `trail` holds only that surface

### Requirement: Each served detail says where it came from
The segment view SHALL carry, for each detail in its `trail`, its source: `community` with the number of distinct riders whose up votes adopted it, else the importer's recorded source for that attribute (`osm`, `usfs`, `nps` or `blm`), else `imported`.

#### Scenario: Imported from the Forest Service
- **WHEN** an importer segment's width came from Forest Service data
- **THEN** its view's sources give `usfs` for width

#### Scenario: Older record
- **WHEN** an importer segment's record holds no sources
- **THEN** its view's sources give `imported` for each of its details

### Requirement: Confirmations and doubts are served
For each detail in effect, `getSegment` SHALL serve the number of distinct riders whose counted confirmation says "still right" in the last 365 days. Once at least 2 riders have confirmed, it SHALL also serve the month of the latest. It SHALL flag a detail as may-have-changed when, in the last 90 days, at least 3 riders said "changed" and they outnumber those who said "still right".

#### Scenario: Confirmed
- **WHEN** 8 riders said "still right" on a segment's dogs value this year, the latest in November
- **THEN** `getSegment` serves 8 confirmations for dogs, latest in November

#### Scenario: One confirmation
- **WHEN** only bob confirmed a detail
- **THEN** `getSegment` serves 1 confirmation and no month

#### Scenario: Doubted
- **WHEN** in the last 90 days 4 riders said "changed" and 1 said "still right" on a detail
- **THEN** `getSegment` flags it as may-have-changed

### Requirement: Open suggestions are served with their totals
`getSegment` SHALL list a segment's open suggestions with each one's detail, value, author, opening date and up and down totals, and for a signed-in viewer their own vote. It SHALL leave out suggestions by athletes the viewer has blocked or muted, or who have blocked the viewer.

#### Scenario: Viewer voted
- **WHEN** bob, who agreed with alice's "No dogs", opens the segment
- **THEN** he sees her suggestion with its totals and his own up vote

### Requirement: Viewers learn whether they may suggest and vote
For a signed-in viewer, `getSegment` SHALL say whether they may suggest (the segment is active and not a lift, and they're a regular), which details are in a cooldown or have their own open suggestion, and whether they may vote or confirm (they have an effort on it). It SHALL also say whether the viewer has an effort on it dated in the last 14 days. It SHALL give these only to that viewer.

#### Scenario: Rider but not regular
- **WHEN** carol, with efforts on 2 dates, opens an active segment
- **THEN** her view says she may vote and confirm, and may not suggest

### Requirement: Labels remove a suggestion or its value
A suggestion whose record or author's account carries `!hide` or `!takedown` SHALL NOT be counted or adopted, nor listed to anyone but, under `!hide` alone, its author. If it was adopted, its value SHALL stop being in effect and the detail SHALL fall back to its previous adopted value, else the importer's. Negating the label SHALL restore it.

#### Scenario: Adopted value taken down
- **WHEN** "No dogs" replaced "Dogs on leash" and its suggestion is labelled `!takedown`
- **THEN** the segment's dogs value is "Dogs on leash" again

### Requirement: Each Trail details row shows its source
On the Segment page each Trail details row SHALL show its source under its value: "OpenStreetMap", "Forest Service", "Park Service", "BLM" or "Imported", or a "Community · N riders" badge. A row with open suggestions SHALL add "N suggestions". A may-have-changed detail SHALL read "Riders report this may have changed". Above the cards, the page SHALL show when the details were last confirmed, by month, if served.

#### Scenario: Community row
- **WHEN** a segment's surface is a community value adopted by 12 riders
- **THEN** its Surface row shows "Community · 12 riders"

#### Scenario: Pending suggestion
- **WHEN** a segment's dogs value has 1 open suggestion
- **THEN** its Dogs row shows "1 suggestion"

### Requirement: Tapping a detail opens its sheet
For a signed-in rider, tapping a Trail details row SHALL open a sheet with the value, its source and confirmation count, "Still right" and "It's changed", the detail's open suggestions with agree and disagree and their totals, and, for a regular outside the detail's cooldown, "Suggest a different value". "It's changed" SHALL record a changed vote and, for a regular, open the suggest picker. Maintained by and Website rows SHALL NOT open a sheet.

#### Scenario: Still right
- **WHEN** bob taps "Still right" in the Dogs sheet
- **THEN** his confirmation is written and the sheet shows it as his

#### Scenario: Not a rider
- **WHEN** a signed-in athlete with no efforts on the segment taps a row
- **THEN** no sheet opens

### Requirement: Regulars suggest from a picker
The suggest picker SHALL list the detail's values in words as the Trail details rows write them. The value in effect SHALL be marked current and not selectable, and a value that is already an open suggestion SHALL say so: choosing it records an agree vote on that suggestion instead of a new suggestion. The picker SHALL say that a suggestion is public and names its author.

#### Scenario: Choosing an open suggestion's value
- **WHEN** alice, a regular, picks "No dogs", which maya already suggested
- **THEN** alice's agree vote on maya's suggestion is written and no suggestion of hers is

### Requirement: Regulars add missing details
A regular SHALL be offered "Add a missing detail", listing the suggestable details the segment has no value for. A segment with no details at all SHALL show, to regulars, an invitation to describe it in place of the Trail details cards. A detail that has only an open suggestion and no value SHALL show the suggested value as a question with its progress, such as "Paved?" and "Suggested · 2 agree · needs 3".

#### Scenario: No details
- **WHEN** a regular opens their friend's segment with no trail details
- **THEN** the page invites them to add its surface, width, lighting, dogs and more

#### Scenario: Not a regular
- **WHEN** an athlete who isn't a regular opens a segment with no trail details
- **THEN** the page shows no Trail details section

### Requirement: Recent riders get a quick check
An athlete whose effort on a segment is dated in the last 14 days SHALL see a card on its Segment page. It lists the details of the folded summary that have values, each with Still right and It's changed buttons, plus "All still right" and "Not now". The card SHALL show once per segment per season on that device. It SHALL not show again after "Not now" or after the athlete has confirmed or changed every detail it lists.

#### Scenario: All still right
- **WHEN** bob, who rode a segment yesterday, taps "All still right"
- **THEN** a still-right confirmation is written for each listed detail and the card goes away

#### Scenario: No recent effort
- **WHEN** bob's latest effort on a segment is 20 days old
- **THEN** its page shows no quick check

### Requirement: Native iOS draws the same flows
On iOS the source captions, detail sheet, suggest picker, missing-detail invitation and quick check SHALL be drawn natively in the shared words: an inset grouped list, a sheet at medium height with Done, and system buttons.

#### Scenario: iOS sheet
- **WHEN** alice taps the Dogs row on her iPhone
- **THEN** a native sheet shows the value, Still Right and It's Changed, and the open suggestions
