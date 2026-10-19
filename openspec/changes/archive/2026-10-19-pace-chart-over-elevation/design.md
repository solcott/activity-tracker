# Design

## Context

- `TimeSeriesChart` draws the elevation backdrop as its own layer, on the end axis, with a fixed range from the lowest point to 1/0.6 of the span above it. Nothing flips it: `inverted` changes only the main line, its other lines, the average line and the marker (`flip`). So by reading the code, higher ground already draws higher behind a pace line. Nobody has seen it yet.
- The Pace card passes `inverted = true` and `fillAbove = false`, so its fill runs from the line down to the slowest pace at the bottom. That is the same layering as heart rate or power over elevation: both fills grow from the bottom, the backdrop at 0.12 alpha and the line's fill at 0.18.
- What differs is how the line moves on a climb. Pace slows on a climb, so the pace line drops where the elevation rises, and the two fills cross more often than heart rate's do (heart rate rises with a climb). That crossing has to be checked to know whether it reads.
- The interval run sample (`WriteWorkoutSamples.intervalRun`) never climbs (`climb = 0`), so its backdrop is a flat band. The MTB ride has hills but is a speed sport.

## Goals / Non-Goals

**Goals:**
- Confirm on a real chart that a run's climbs draw rising behind its pace line, and fix the layering if the line or fill gets lost.

**Non-Goals:**
- Changing the backdrop for any other card, or the 600dp threshold (see the archived `activity-detail-redesign` Measurements).
- A pace backdrop on phones.

## Decisions

### 1. Hills go into the interval run sample rather than a new file

The intervals become hill repeats: each 4-minute hard stretch climbs (0.25 m/s, so 60 m each) and each 2-minute jog comes back down. The hard stretches are also the fastest, so on the repeats pace and elevation rise together, the way heart rate does. A cool-down that climbs 30 m slowly gives the opposite case, where pace drops while the ground rises. A hill repeat is a common run workout, so the sample stays realistic and covers both cases. The samples' consistency checks already derive every average from the points written, so adding a climb keeps them consistent.

- *Alternative:* add a separate hilly run file. That means one more sample to keep, plus a change to `dev/sample-activity-files`' list, just to vary a stream the interval run already has.

### 2. Keep the pace fill unless the check shows it hides the climb

The layering matches every other card, so the default is to change nothing. If the fills crossing makes the line hard to follow, the first lever is drawing the pace line's fill more faintly when there's a backdrop, which is local to `TimeSeriesChart`. Dropping the fill on the Pace card comes second, since it then looks unlike every other card.

## Risks / Trade-offs

- [The check finds nothing to fix] → Then the change is a sample with hills and a spec scenario that pins the behavior down, which is still worth having.
- [Hills change the interval run's figures (elevation gain, descent, grade)] → Nothing asserts the interval run's elevation today; the consistency checks recompute it from the points.
