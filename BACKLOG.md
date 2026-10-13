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

Profile details an athlete marks Mutuals or Only me (location, birthdate, gender, weight and height) live in the AppView's `actor_private_details` table, because repo records are public. atproto Spaces (permissioned repos on the athlete's own PDS) are the native home for that data, but in the August 2026 alpha they have had no security audit, their APIs and schemas may break, and the hosted sandbox will be wiped. Revisit when Spaces reaches a stable release.

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

## Keep more of what activity files hold

FIT, GPX, TCX, Shearwater dive XML, and UDDF and Subsurface logbook import (optionally gzipped) works on every client (`:core-activityfile`, `ImportActivityScreen`). Next:

- **Dive data.** `ImportedDive` (max and average depth, gases, GF, CNS, surface interval, depth profile) shows on the import screen, but the activity lexicon has no dive fields, so a published dive keeps only its time, water temperature and sub-sport. Add the summary fields with the `lexicon-change` skill and show them on activity cards. The depth profile is already in the activity's streams. FIT dive logs (Garmin Descent) fill the same `ImportedDive` and add bottom time, OTU, air consumption (RMV or pressure SAC), per-tank start/end pressure and the entry/exit position; carry those into the lexicon too. FIT's per-sample deco state (NDL, next stop, TTS, CNS/N₂ load, ppO₂, tank pressure) could join the streams.
- **Ride data.** Power (avg, max, NP, work, and FTP-based IF and TSS), cadence, descent, electronic shifting (shift counts, time in each gear) and Garmin's mountain bike dynamics (jumps, Grit, Flow) show on the import screen (`ImportedActivity`, `RideMetrics`), but records don't carry them. Some, like FTP, IF and TSS, say a lot about an athlete's fitness, so decide which are public before adding them with the `lexicon-change` skill; the rest wait for a private store. Sensor make and model already publish (`activity#sensor`). Laps are still dropped.
- **Gear teeth.** FIT gear changes carry teeth from the device's gearing setup, which is often a default: an Edge 840 with SRAM AXS on a 10–52T Eagle bike reports a 39T ring and 10–26T cassette. The import screen shows gear numbers only. Showing teeth needs the athlete's own gearing, for example on a bike in a future gear list.
- **More streams.** Activity streams (`feed.activityStreams`) carry distance, altitude, speed, heart rate, power, cadence, temperature, depth and rear/front gear changes. Still dropped: laps, left/right balance, FIT dive deco state (NDL, TTS, ppO₂, tank pressure) and running dynamics. Activities published before streams have none; there's no backfill.
- **Duplicates beyond dives.** Imports flag duplicates of dives only (`DiveMatcher`); runs and rides imported twice (a watch and a bike computer on one ride) could use the same check and the same merge or group actions. A logbook dive twice in the file can't be merged into the other yet, only imported, grouped or ignored.
- **Bulk import.** Strava's account export is a zip of gzipped files plus `activities.csv` with titles. Reading the zip and importing many activities at once would reuse `ActivityFileReader`.
- **Track blob MIME type.** The local PDS stores the GPX `track` blob as `application/xml`, not the lexicon's `application/gpx+xml`. Check whether a stricter PDS rejects it.

## Before deploying anywhere shared (QA, staging, prod)

Checks that only matter once the AppView runs on a database other than the local Docker one.

- **Search needs `pg_trgm`.** The PostgreSQL-only migration `db/vendor/postgresql/V6__actor_search_trigram_indexes.sql` runs `CREATE EXTENSION IF NOT EXISTS pg_trgm`. Since PostgreSQL 13 it's a trusted extension, so a role that owns the database (or has `CREATE` on it) can create it. That's untested, though: the local `appview` role is a superuser. Check the role on each target, or have an admin create the extension first so the migration's `IF NOT EXISTS` skips it. If it fails, Flyway stops and the AppView doesn't start.
- **Check the search plan at realistic size.** Once `actors` has a realistic number of rows, run `EXPLAIN ANALYZE` on `actor.searchActors`. Trigram indexes serve queries of three or more characters. A one- or two-character query scans `actors`, and deep pages cost more because paging is by offset. If that's too slow, require three characters, or match only handle and name prefixes for shorter queries.
- **Spaces need a real lexicon namespace.** A PDS grants the `space:` OAuth scope only if it can resolve the space type's lexicon declaration. Locally, `PDS_LEXICON_AUTHORITY_DID` points the PDS at a test account that publishes it (see `spaces-client-protocol`). Anywhere else, the NSID authority must resolve through DNS: a `_lexicon` TXT record on the reversed namespace domain pointing at the DID that publishes the `com.atproto.lexicon.schema` records. `Nsid.NAMESPACE` (`app.activitytracker`) is a placeholder, so pick a domain we control first, then rename the namespace across `Nsid`, `lexicons/` and `create-account.sh`.

## More training zone options

Each sport can override the default thresholds (`actor.defs#sportTraining`, `SportZonesScreen`). Run and swim have Friel pace zones; everything else uses heart rate and power. Next:

- **Custom zones.** Let athletes edit zone boundaries and names, as TrainingPeaks does. Store them as explicit boundary arrays (say `heartRateZones`, `powerZones`, `speedZones`) on `#training` and `#sportTraining`, plus a `custom` model token. `Zones.compute` would then read boundaries from the record instead of a `ZoneModel`.
- **Threshold from a test.** Work out threshold pace or power from a test: distance plus time or pace (a 30-minute run, a CSS 400/200 swim), or a 20-minute power test.
- **More pace models.** 80/20, USAT, PZI, CTS and MyProCoach, each from a sourced, published formula. Row pace per 500 m needs a model before `SportZones.kinds` offers it.

## Move activity streams to a Space before going live

Activity streams (`app.activitytracker.feed.activityStreams`, under their activity's rkey) are public records on the athlete's PDS with a public JSON blob, even when the athlete picks Mutuals or Only me. The AppView's `feed.getActivityStreams` enforces that visibility, and strips the zone snapshots (which reveal FTP and threshold heart rate) unless the viewer may see the athlete's training, but anyone reading the repo with `getRecord`, `sync.getBlob` or the firehose sees everything. That's acceptable only while this runs on the local dev stack. Move them before anything shared, alongside the private profile details (see above).

- **Client seam.** `ActivityStreamsStore` (`core/data/.../activity`) is the only writer; bind a Spaces implementation in place of `PdsActivityStreamsStore`. Clients read streams only through `FeedRepository.streams` → `feed.getActivityStreams`, so reads don't change.
- **AppView seam.** `StreamSource` (`api/ActivityStreamsViews.kt`) is the only reader of the data; replace `PdsBlobStreamSource` with one that reads the space as a member. Visibility can stay enforced in `ActivityStreamsViews`, or follow space membership (the same Mutuals question as the profile details).
- **Migration.** Have clients copy each of their streams into their space and delete the public records and blobs, then drop `activity_streams.blob_cid` and the `activity_stream_data` cache in a new migration.

## Videos on activities

Activities carry photos (`feed.activity#media`, an open union with `#image` only). Videos join it as a `#video` member, and the code is laid out for that: `ActivityMedia`/`ActivityMediaView` decode an unknown member as `Unknown` and write it back untouched, `MediaItem` (`:app-screens`) gets a `Video` case whose `when`s then fail to compile wherever they need handling (`MediaRow`, the gallery, the details form), and the AppView's `/img/media/{did}/{cid}` redirect serves any blob an indexed activity lists.

- **Lexicon.** `#video`: a `video/mp4` blob of at most about 50 MB, `aspectRatio`, an optional `thumb` image blob for the feed, and `alt`. The view gets `#videoView` with the video's and the thumbnail's URLs.
- **Compression.** Phones re-encode to 720p H.264/AAC before upload: Media3 Transformer on Android, `AVAssetExportSession` on iOS. Desktop and web have no encoder, so they upload only a file already under the cap and refuse a larger one.
- **Playback.** `ComposeMediaPlayer` (`io.github.kdroidfilter:composemediaplayer`) covers Android, iOS, desktop and web. The feed row shows the poster frame with a play badge, and the gallery plays it.
- **Uploads.** A video is too big to hold as a `ByteArray` in memory or in the Room outbox. Upload from a file path, streamed, and let the outbox carry several file-backed blobs per write, so a video posts even after the app restarts.
- **PDS limit.** The reference PDS caps blobs with `PDS_BLOB_UPLOAD_LIMIT`; raise it in `docker/compose.yaml` and expect other PDSes to refuse large videos with a clear error.

