# Design

## Context

`PerSecond.hold` (core/activityfile) repeats each sample's value for the gap to the next, but a gap over `MAX_HOLD_SECONDS = 5` counts once. `StreamAnalytics.perSecond` feeds it for time in zone and the power curve (computed when the page is shown, from the stored streams), and `RideMetrics` for normalized power at import. A steady 30 s TCX therefore yields one second per sample: 16 samples, 0:16.

## Goals / Non-Goals

**Goals:** count steady sparse recording in full; keep pauses out; leave 1 s and smart-recorded files exactly as they are.

**Non-Goals:** reading FIT timer start/stop events (not stored today); recomputing stored normalized power for older imports; changing chart smoothing (`averaged`).

## Decisions

- **Hold limit from the stream's own interval.** The limit becomes `max(MAX_HOLD_SECONDS, k × typical gap)`, where the typical gap is the median gap between consecutive samples and `k` is about 2. Median rather than mean, so a few long pauses don't stretch it. For 1 s and smart recording the median is 1–2 s, so the limit stays 5 s and results don't move. Alternatives: a larger fixed limit (would count real pauses in 1 s files); using the activity's moving time to scale (zones would no longer add up per sample, and moving time isn't always stored).
- **A gap over the limit still counts one second**, as today. Counting one typical interval instead would be slightly truer for sparse files, but for 1 s files the two are equal, and for sparse ones the difference is under one interval per pause, within the spec's tolerance. Keeping one second means no change to existing results.
- **Compute the median once per call to `hold`**, over the samples it is given; no API change for callers. With fewer than 3 gaps there is no usual interval to read (two samples ten minutes apart are a pause, not a 10-minute recording interval), so the limit stays 5 s.

## Risks / Trade-offs

- [A sparse file with many pauses has a median near the pause length] → pauses would be rare relative to samples in any real recording; the 2× factor bounds the error.
- [Normalized power of older sparse imports stays wrong] → accepted; the 30 s rolling window makes NP rarely meaningful for 30 s data anyway.
