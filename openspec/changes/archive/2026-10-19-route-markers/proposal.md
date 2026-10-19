# Proposal

## Why

An activity's map shows its line but not which way it went: on a loop or an out-and-back you can't tell where the athlete started or finished. Now that the line breaks where no position was recorded (`map-gaps`), each break also leaves two loose ends that look like the start or finish of the activity.

## What Changes

- Every route map (the detail page, the import preview and the feed card) marks where the activity starts (a green dot) and ends (a red dot), each ringed in white.
- Each break in the line marks both its ends with a smaller hollow ring in the route's colour, so a break reads as "the line carries on over there", not as a second start or finish.
- Markers respect privacy zones: viewers' maps mark the ends of the trimmed polyline they see, and the owner's map, which draws the whole untrimmed track, marks the start and end where the route others see starts and ends, so a marker never points at home.
- The markers are derived from the stored polyline and `polylineGaps`; nothing new is stored, so they appear on every activity at once, including combined ones (whose breaks fall between members).

## Capabilities

### New Capabilities

- `feed/route-markers`: the start, end and break markers on an activity's map.

### Modified Capabilities

None.

## Impact

- `core/data`: `PrivacySettings.shownEnds` (the trimmed route's ends, as published) and `ActivityRepository.privacySettings()`.
- `app/screens`/`app/presenters`: `RouteEnds`, and the detail state's `ownRouteEnds` from `OwnRoute`.
- `composeApp`: a shared helper that picks the marker points from a route's pieces; `ActivityMap` (MapLibre) adds circle layers for them, and `RoutePreview` (web and feed cards) draws them on its canvas.
