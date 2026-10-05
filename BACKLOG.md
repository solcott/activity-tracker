# Backlog

Planned work that isn't ready to start yet. Add new items at the bottom. Delete an item when it ships, because git history keeps the record.

## Suggest athletes from the user's Bluesky follows

Follows in this app (`app.activitytracker.graph.follow`) are separate from Bluesky follows (`app.bsky.graph.follow`). Both are records in the same repo on the user's PDS, but the AppView indexes only `Nsid.indexedCollections`, so a new user starts with an empty network. Suggest the athletes the user already follows on Bluesky who also use this app, and let them follow those athletes here with one tap.

- **Suggest, don't merge.** Following someone from a suggestion writes our own follow record through `ProfileRepository.follow`. Never write `app.bsky.*` records, and keep this app's follow graph separate from Bluesky's.
- **Source of Bluesky follows.** Prefer having the client list the user's own `app.bsky.graph.follow` records from their PDS with `com.atproto.repo.listRecords`, so the AppView never touches Bluesky data. The descriptor is hand-written in `core/lexicon/.../atproto/Repo.kt`, because client code can't use an atproto SDK.
- **AppView query.** For example `app.activitytracker.graph.getSuggestedFollows`, or a batch `actor.getProfiles`. It takes the candidate DIDs, caps how many it accepts, keeps the ones the AppView knows, and excludes accounts the viewer already follows here.
- **Open questions.** Where suggestions appear: the empty feed, the user's own profile, or a screen of their own. Whether to filter to "has posted an activity" or "has an `actor.profile`".
- **Privacy.** Bluesky follows are already public. Store nothing new beyond the follows the user chooses to create.

## Move private profile details to an atproto Space

Profile details an athlete marks Mutuals or Only me (location, birthdate, gender, weight) live in the AppView's `actor_private_details` table, because repo records are public. atproto Spaces (permissioned repos on the athlete's own PDS) are the native home for that data, but in the August 2026 alpha they have had no security audit, their APIs and schemas may break, and the hosted sandbox will be wiped. Revisit when Spaces reaches a stable release.

- **Target.** One space per athlete, with the athlete as its authority, holding a details record. The AppView (or each client) reads it as a member instead of storing a copy.
- **Mutuals.** A space grants access by membership, not by a rule over the follow graph. Decide whether to keep the space's member list in sync with the athlete's mutuals, or to keep enforcing visibility in the AppView and use the space only for storage.
- **Migration.** Have clients copy their own rows into their space, switch reads over, then drop `actor_private_details` in a new migration. Public details stay in `actor.profile`.
- **Hard rule still applies.** Client code can't use an atproto SDK, so the Spaces XRPC calls are hand-written in `core/atproto`.

## Show activities on real maps

The activity detail screen shows the route on a MapLibre map (`ActivityMap`) on Android, iOS and desktop. Web still draws the Canvas `RoutePreview`. What's left:

- **Web map (blocked upstream).** MapLibre Compose 0.19.0 has no wasmJs target, only Kotlin/JS. Adding one is [maplibre/maplibre-compose#209](https://github.com/maplibre/maplibre-compose/issues/209), labelled `blocked-upstream`: it needs Compose Multiplatform 1.13, which has the API for compositing a WebGL map into Compose UI. That works in CMP 1.13 prereleases (maplibre-compose PR #1081). Once a MapLibre Compose release ships wasmJs, and we're on CMP 1.13:
  - Move `libs.maplibre.compose` from `nonWebMain` to `commonMain` in `composeApp`.
  - Merge `ActivityMap.nonWeb.kt` into `commonMain` and delete the wasmJs actual.
  - Follow the release's web setup steps in `:webApp` (e.g. `installMapLibreCompose()` before `ComposeViewport`).
- **Renderer notes.** Every MapLibre Compose target is still alpha or beta, so MapLibre's API stays inside `ActivityMap`. Keep `RoutePreview` as the fallback. The track is a GeoJSON line layer, so it can later be coloured by speed, HR or elevation. The map's pan and zoom are off inside the detail list; a tap-to-open full-screen map would be the interactive one.
- **Tiles.** Use OpenFreeMap (no key, no limits) by default and in dev. Use MapTiler Outdoor/Topo or Stadia Outdoors for a trail and contour style. Self-hosted Protomaps PMTiles on S3 or R2 behind a CDN is the cost-control option. Keep the style URL in config so providers can change without a release.
- **Avoid.** Google Maps SDK (no wasmJs or desktop target, Google tiles only, billed per load). Mapbox v2+ (proprietary licence, billed per active user). `tile.openstreetmap.org` (its usage policy forbids app traffic). MapKit (iOS only).
- **Feed vs detail.** Use the interactive map only on the activity detail screen. Feed cards stay static. Near term that's the Canvas polyline. Later, the AppView renders a PNG per activity at index time, using a provider's Static Maps API or self-hosted tileserver-gl, and serves it from an `app.activitytracker.*` endpoint.
- **Attribution.** Every OSM-based provider requires "© OpenStreetMap contributors" plus the provider's own credit to be visible. Keep MapLibre's attribution control on.
- **Privacy zones.** Repo records are public, and imported files carry exact start and end points, often a home address. Trim the polyline around the athlete's privacy zones before `putRecord`. Never put the full-resolution track in a record.

## Import GPX and TCX, and keep more of what FIT files hold

FIT import works on every client (`:core-activityfile`, `ImportActivityScreen`). Next:

- **GPX and TCX.** Both are XML, so parse them in `commonMain` (no platform SDK needed) into the same `ImportedActivity`, and widen the extension check in `ImportActivityPresenter`. TCX carries laps and heart rate; GPX heart rate lives in Garmin's `TrackPointExtension`.
- **Power, cadence and laps.** `FitActivity` already decodes them, but the activity lexicon has no fields for them, so they're dropped. Add them with the `lexicon-change` skill.
- **Streams.** Per-sample HR, power and elevation for charts and a coloured map line need a home that isn't a public record (see privacy zones above). The AppView can already parse FIT (`GarminJavaFitDecoder` is on its classpath), so a private upload procedure like `actor.putPrivateDetails` is one option.
- **Track blob MIME type.** The local PDS stores the GPX `track` blob as `application/xml`, not the lexicon's `application/gpx+xml`. Check whether a stricter PDS rejects it.
