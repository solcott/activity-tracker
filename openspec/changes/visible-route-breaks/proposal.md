# Proposal

## Why

A ride paused by accident for 7 minutes while the athlete rode on 288 m breaks its map line as it should, but nobody can see where: the break markers are 5 dp dots ringed in the route's own colour, so they read as the line's end caps, and nothing on the map ties the two ends of a break together. A zoomed-out card shows a small kink, and a zoomed-in map puts the two ends far apart with no hint that they belong together.

## What Changes

- A break in the map line is joined by a dashed connector between the last position before it and the first after it, in the break colour, so the gap reads as "not recorded" rather than vanishing. It is never drawn solid, so the map still draws no route across ground the device didn't record.
- Break markers get their own colour (amber), distinct from the route, the start marker and the end marker, and grow to the start and end markers' size.
- The two ends of a break are told apart by shape: a solid dot where recording stopped, a ringed dot where it resumed.
- The change covers every route map: the detail page, the enlarged map, feed cards, the import preview, and the web map. It needs no data change: `polylineGaps` already says where the breaks are, so activities saved before this get the new look.

Out of scope: telling a timer pause apart from a GPS dropout (the FIT reader ignores timer events, and `polylineGaps` stores indices only), a tap label on a break ("Paused 7:01 · 288 m not recorded"), and counting a break's distance in the activity's total.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `feed/map-breaks`: a break draws a dashed connector between its ends where it used to draw nothing.
- `feed/route-markers`: break markers differ from the start and end by colour and shape, no longer by being smaller, and the stop and resume ends differ from each other.

## Impact

- `composeApp`: `RouteMarkers` (colours, radii, which end of a break each marker is), `ActivityMap.nonWeb` (MapLibre layers for the connector and the two break-marker kinds), `RoutePreview` (the Canvas renderer behind feed cards, the import preview and the web map).
- No change to lexicons, `core/data`, `RouteBreaks`, stored records or the AppView.
