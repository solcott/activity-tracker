# Proposal

## Why

A metric card's headline and its chart can disagree about the same average. On the pool swim the Pace card's headline says 1:59 /100 yd, while the chart's dashed average line and its screen-reader text say 2:00. The headline takes the average stored with the activity (from the file or the recording), and the chart takes the mean of the stream's samples. The two are computed differently, and either can be off by a unit. Heart rate, power, speed, cadence and temperature cards can show the same gap.

## What Changes

- Each metric card's chart marks the same average its headline gives. The average and maximum in the chart's screen-reader description are the headline's too.
- Where the activity stores no average or maximum for a metric, the card keeps using the stream's, as it does today for the headline.
- Cards whose headline gives no stream average keep their chart as it is: dives, running dynamics, cycling dynamics, tanks, the left/right legs' header.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `feed/activity-page-layout`: the Metric card contents requirement says a card's chart marks the average its headline gives.

## Impact

- `app/presenters`: `detail/MetricCards.kt` (the series a card carries).
- The activity page and the import preview alike, since both build cards the same way.
- No change to stored data, lexicons, the AppView or the avg/max table, which already uses the stored figures.
