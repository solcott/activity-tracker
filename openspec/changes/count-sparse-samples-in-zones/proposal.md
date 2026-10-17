# Proposal

## Why

An imported TCX interval run that logs a sample every 30 s shows 0:16 of heart-rate zone time for an 8:00 run. Time in zone, the power curve and normalized power all resample a stream to one value per second, and treat any gap over 5 s as a pause that counts once. That fits 1 s recording and Garmin's smart recording with short dropouts, but a file recorded at a slower, steady interval (many TCX exports, older devices, some apps' 10–30 s logging) loses almost all of its time.

## What Changes

- Per-second resampling holds each sample until the next across gaps up to the stream's own recording interval with some slack, not a fixed 5 s, so a steady 30 s file counts every second it covers.
- A gap well beyond the stream's usual interval (auto-pause, a dropout) still counts once, as today.
- Time in zone, the power curve and normalized power use the same rule. Nothing about 1 s files changes.
- Charts and zones are computed when the page is shown, so already imported activities correct themselves; normalized power is computed at import and is fixed for new imports only.

## Capabilities

### New Capabilities
- `feed/stream-resampling`: how a stream's samples become per-second values for time in zone, the power curve and normalized power, across irregular and sparse recording.

### Modified Capabilities
None.

## Impact

- `core/activityfile` `PerSecond.hold` (and its callers `RideMetrics`, `StreamAnalytics.perSecond`/`timeInZones`).
- `app-presenters` `ChartsBuilder` reads the result; no change expected there.
- Tests in `core/activityfile` and `core/data`.
