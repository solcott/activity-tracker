# Design

## Context

`combine-activities` stores `combined.sources`, a ranked member list per channel, and rebuilds the combined activity with `ActivityCombiner` from its members whenever membership changes. Each member's record already names its `device`. The activity page already loads `activityView.members`, and for the owner it already loads each member's stream parts through `feed.streams`.

## Goals / Non-Goals

**Goals:**
- Choosing a source means one choice per kind, not ranking every channel by hand.
- Chart captions need no new stored data.

**Non-Goals:**
- Choosing per time range ("watch until 10:40, then the phone"). A handover comes from the combined timeline, not from a choice.
- Picking sources at import time. Import's combine (`combine-on-import`) uses the defaults, and the athlete changes them afterwards.

## Decisions

### D1. A fixed map of kinds to channels
`DataKind` in `core/data` maps each kind to its `ChannelSource.channel` values:

| Kind | Channels |
|---|---|
| map | `position` |
| distanceSpeed | `distance`, `speed` |
| elevation | `altitude` |
| heartRate | `heartRate`, `respirationRate` |
| power | `power`, `leftRightBalance`, `torqueEffectiveness*`, `pedalSmoothness*`, `pco*` |
| cadence | `cadence` |
| temperature | `temperature`, `coreTemperature` |
| runningDynamics | `verticalOscillation`, `stanceTime`, `stepLength`, `verticalRatio`, `stanceTimeBalance`, `formPower`, `legSpringStiffness` |
| laps | `laps`, `sets`, `lengths` |
| dive | `depth`, `dive`, and the dive state channels |

A channel not in the table (a future one) keeps its default ranking.

- **Rejected: a ranking per channel in the UI.** It's a long list nobody wants, and splitting power from its balance data across devices gives charts that disagree.

### D2. Choosing rewrites the ranking and rebuilds
`ActivityManager.setSources(combined, kind, member)`:
1. For each of the kind's channels, it moves `member` to the front of that channel's ranking, keeping the rest in order.
2. It writes the record and rebuilds through the same path as adding a member (`combine-activities` D7).

The rebuild reads members' data again rather than caching it, because a choice is rare and members never change.

### D3. Attributing charts
- `ActivityCombiner` returns `attribution: Map<channel, List<(member, fromSeconds, toSeconds)>>` along with what it builds.
- That isn't stored. The page works it out again from `combined.sources` and the member spans, which every viewer can see in `activityView.members` (uri, startedAt, elapsedTime) and in the record.
- So that every viewer gets the same answer, attribution uses spans and ranking only, not sample-level gaps: a member is credited for its whole span while it's the top-ranked member covering it.
  - The caption may name a device for a short stretch where that device actually had a gap. That's acceptable for a caption.
- **Rejected: storing per-sample source indices in the streams blob.** It's more exact, but it means changing the `streamData` lexicon for a caption.

### D4. UI
- **Sources section:** in the owner's part of the page, one row per listed kind showing the chosen member's name, with a menu of the members that have that kind.
  - While a rebuild runs, the row shows progress through `flights`, and it shows an error with a retry if the rebuild fails.
- **Chart captions:** use `MemberView.device`, falling back to "Recording started 9:02".

## Risks / Trade-offs

- [A caption names a device for a short gap the next member filled] → Accepted (D3). The figures themselves are exact.
- [A rebuild after a source change takes a few seconds on long activities] → It runs off the main thread through the outbox, and the page keeps showing the previous data until the rebuild lands.
