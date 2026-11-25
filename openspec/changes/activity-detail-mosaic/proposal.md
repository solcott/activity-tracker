# Proposal

## Why

The activity page opens with a text header and shows the map and photos in a 240dp sideways row, so the two things a person looks at first, where it was and what it looked like, are small and partly off screen. Below that, every metric card is open: a ride with a power meter is about ten full charts, the laps table and 40 stats, so getting to segments or the stats takes four or five screens. A manual walk is the opposite: a page of figures with empty ground. The athlete reviewing their own effort and a friend coming from the feed need the same page to work at both ends.

## What Changes

- **Media mosaic.** The page opens with a bento of the route map (the large tile) and up to two photos stacked beside it, the last one saying "+N" when there are more. Photos alone fill the mosaic when there's no route, one photo fills it alone, and with neither there's no mosaic and the title leads.
- **A lead card by sport, the rest as strips.** One chart card opens at full size: the one that matters most for the sport (power on a ride that has it, else heart rate; pace on foot or in the pool; depth on a dive; the runs on a ski day; the sets in a workout). The other charts show as one-line strips (title, headline average and maximum, a sparkline) that open in place. This reverses the 2026-10-19 redesign's "always open" rule: with every card open, a well-equipped ride is too long to scan.
- **Tables preview five rows.** Laps, lengths, sets and legs show their first five rows, then Show all.
- **Section chips.** On one-column pages, once the mosaic scrolls away, chips for the sections the activity has stick under the bar and jump to them.
- **All data.** Every stat, the avg/max table, the full tables and where a combined activity's data came from move to an All data page, opened from the end of the page and from every Show all. Wide windows show it in the side pane instead.
- **Little data reads as a short post.** No empty cards; three or fewer hero figures are set larger in one row.
- **Not in this change:** a run-by-run bar chart for ski days, photos placed on the map or timeline (photos carry no time or place), and remembering which strips an athlete opened.

## Capabilities

### Modified Capabilities
- `feed/activity-page-layout`: the media mosaic, the lead card and strips, five-row tables, section chips, the All data page, and the layouts by width on Compose and iOS.

## Impact

- **app-common-api / presenter:** `ActivityPageContent.cards` ordered by sport with the lead first; `MediaMosaic`, the chips and the table preview size as shared rules.
- **feature/detail:** `ActivityDetailPage` gains the open cards and the side pane's All data; new `ActivityDataScreen` (api, presenter, ui) and its fixtures; `ActivityDetailUi` rebuilt around the mosaic, strips and chips.
- **iOS:** `ActivityPage.swift` and `ActivityPageCards.swift` follow, and a native All data page.
- **Tests:** card order by sport, strip toggles, the All data presenter, screenshot and snapshot references.
- **No** lexicon, AppView or data-layer changes.
