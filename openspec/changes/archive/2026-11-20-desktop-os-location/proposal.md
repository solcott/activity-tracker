# Proposal

## Why

In the desktop app, the Search tab's Trails & roads map (and Dive sites, which opens wherever it does) never opens at the athlete's location: desktop has no location source, so the map opens at the start of their latest activity, or at a prompt to move the map. An athlete on a laptop at home or travelling expects to land where they are, as they do on a phone, and every desktop OS has a location service that can say.

## What Changes

- The desktop app asks the operating system for one approximate location when map browse first opens (macOS Location Services, Windows location, Linux GeoClue), and centres the map there. The OS shows its own permission prompt; the app shows none.
- When the OS gives nothing (refused, turned off, no provider, no answer in time), browse opens as it does now: at the latest activity's start, else the prompt.
- If the OS answers only after browse has opened at the fallback (an athlete reading the permission prompt), the map moves to the location then, unless the athlete has already moved it.
- The location is read once per browse screen for opening it and never recorded; desktop still offers no Record. The coordinates stay on the device; only the map's area goes to the AppView, as on phones.
- Web is unchanged: it has no map.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `segments/segment-browse`: "Browse opens at the athlete's location" covers desktop, through the OS's location service, and adds the late answer that moves only an untouched map. `dive-sites/site-browse` opens where segment browse does, so it follows without its own delta.

## Impact

- `core/location`: a one-shot location contract beside `LocationTracker`, with mobile and web bindings.
- `app/common/presenter` `MapBrowse` and the segment and dive site browse presenters: use it, and let a late fix move an untouched map.
- `desktopApp`: the desktop binding and three OS bridges: a small Swift helper app built on macOS hosts, a PowerShell (WinRT) call on Windows, GeoClue2 over D-Bus on Linux (new dependency `dbus-java`).
- No lexicon, AppView or iOS/Android UI change.
