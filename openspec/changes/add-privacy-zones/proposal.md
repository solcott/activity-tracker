# Proposal

## Why

An activity's `polyline` shows exactly where it started and ended, which is often the athlete's home or work. Recordings start at the front door, and imported files carry exact start and end points. The polyline is in every activity record: public ones in the repo, where anyone can copy it from the firehose, and non-public ones in tier spaces, which the AppView stores too. The `feed.activity` lexicon already says privacy zones must be trimmed before writing, but nothing does it. Strava and Garmin both let athletes hide the parts of a route near places they choose.

## What Changes

- **Privacy zones.** An athlete keeps a list of zones, each a center and a radius (Strava offers 200 m to 1.6 km), with an optional label. They're stored as one `app.activitytracker.actor.privacyZones` record (rkey `self`) in the athlete's `self` space, never in the repo, and read once per session like `actor.activityDefaults`. Coordinates are integers (1e-7 degrees), since lexicons have no floats.
- **Offset centers.** When a zone is saved, its stored center is moved by a random amount within the radius, as Strava does. That stops the hole that repeated activities leave in their routes from pointing at the real address.
- **Hide start and end.** An optional per-athlete distance that trims the first and last part of every route, wherever it is. It covers places the athlete hasn't made a zone for, such as an imported activity from a friend's house.
- **Trim at write time.** `ActivityDraft.polylineOf` is the single place polylines are built, for recordings and imports alike. It drops the points inside any zone or the start/end distance, cutting at the boundary. If nothing is left, the activity has no polyline. Totals (distance, time, elevation) and streams are unchanged; streams hold no positions.
- **Every visibility is trimmed, Only me included.** The AppView syncs every tier and stores what it holds, so an untrimmed Only-me polyline would still put a home address in its database. The athlete still sees their full route: on their own activity, the detail map draws from the `feed.activityTrack` in `self` (`keep-full-track-only-me`) when the session can read it.
- **Retroactive.** Adding, moving or removing a zone, or changing the start/end distance, re-trims the athlete's existing activities: each is rebuilt from its full track and rewritten in place through the outbox, as a sweep under the same lock as the zones repair. Activities without a track (manual entries) aren't affected. Removing a zone restores the hidden part, again from the track.
- **Settings screen.** Lists zones and lets the athlete add one from the start or end of one of their own activities, or from their current location on phones. A map picker waits for maps on every platform (web is blocked upstream).
- **Kept full:** the `feed.activityTrack` and `feed.activitySource` records stay untrimmed in `self`, as do exports (`export-activity-files`, `keep-original-activity-files`), because only the athlete reads them. Future location fields, such as dive entry and exit positions from the "Dive data" bullet in BACKLOG.md, must go through the same trimming or stay in `self`.

## Open questions

- Whether new athletes get hide-start/end on by default, as Strava now does, and at what distance.
- Whether re-trimming touches activities whose visibility is Only me, or only those others can see. Trimming all of them keeps addresses out of the AppView's database, but costs more rewrites.

## Capabilities

### New Capabilities
- `profile/privacy-zones`: where an athlete's privacy zones and start/end setting are stored, how routes are trimmed by them at write time and retroactively, and what stays untrimmed for the athlete.

### Modified Capabilities

## Impact

- Lexicons: new `actor/privacyZones.json`, `space/athlete.json` (collections + description), `feed/activity.json` (`polyline` description: trimmed by the athlete's privacy zones), `Nsid`, `core/lexicon` models. Republish local lexicons and sign in again.
- `core/location`: trimming in `TrackMath` (circle test, cut at the boundary, start/end distance).
- `core/data`: a `PrivacyZones` store like `ActivityDefaults`, `ActivityDraft.polylineOf`, the import and record paths, and the retroactive sweep in `ActivityRepository` (reads tracks from `self`, rewrites polylines through the outbox).
- UI: a new privacy-zones settings screen (`new-screen` skill), "Hide this start/end" on the athlete's own activity, and the owner's detail map using the full track.
- AppView: no change. It only ever sees trimmed polylines.
- Depends on `keep-full-track-only-me`.
