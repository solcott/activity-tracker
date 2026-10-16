# Backlog

Planned work that isn't ready to start yet. Add new items at the bottom. Delete an item when it ships, because git history keeps the record.

## Suggest athletes from the user's Bluesky follows

Follows in this app (`app.activitytracker.graph.follow`) are separate from Bluesky follows (`app.bsky.graph.follow`). Both are records in the same repo on the user's PDS, but the AppView indexes only `Nsid.indexedCollections`, so a new user starts with an empty network. Suggest the athletes the user already follows on Bluesky who also use this app, and let them follow those athletes here with one tap.

- **Suggest, don't merge.** Following someone from a suggestion writes our own follow record through `ProfileRepository.follow`. Never write `app.bsky.*` records, and keep this app's follow graph separate from Bluesky's.
- **Source of Bluesky follows.** Prefer having the client list the user's own `app.bsky.graph.follow` records from their PDS with `com.atproto.repo.listRecords`, so the AppView never touches Bluesky data. The descriptor is hand-written in `core/lexicon/.../atproto/Repo.kt`, because client code can't use an atproto SDK.
- **AppView query.** For example `app.activitytracker.graph.getSuggestedFollows`, or a batch `actor.getProfiles`. It takes the candidate DIDs, caps how many it accepts, keeps the ones the AppView knows, and excludes accounts the viewer already follows here.
- **Open questions.** Where suggestions appear: the empty feed, the user's own profile, or a screen of their own. Whether to filter to "has posted an activity" or "has an `actor.profile`".
- **Privacy.** Bluesky follows are already public. Store nothing new beyond the follows the user chooses to create.

## Private activities: what's left

Each activity is Public, Followers, Mutuals or Only me (`add-activity-visibility`): a non-public one is a `feed.activity` in the athlete's tier space for it, the AppView syncs those spaces as a member and serves each activity only to the viewers its tier admits, and new activities start at the athlete's defaults (`actor.activityDefaults`, in their `self` space). What's left:

- **Narrowing can't recall copies.** A Public activity made non-public stays in whatever copied it from the firehose meanwhile. The edit screen warns; there's nothing more to do on our side.
- **Delete-then-write races.** A visibility change writes the new place before deleting the old, and the AppView shows the narrower copy while both exist. A notification lost on both sides is covered by the 15-minute reconcile; tune that (`SpaceSyncer`) once there's real traffic.

## Private profile details in Spaces: what's left

Non-public profile details live in each athlete's `app.activitytracker.space.athlete` tier spaces (`mutuals`, `self`), one `actor.privateDetails` record each, with the AppView as managing app. Spaces are an alpha (August 2026: no security audit, breaking changes weekly, the hosted sandbox will be wiped), so revisit the pinned image and `Space.kt` when they reach a stable release.

- **Real namespace first.** See "Spaces need a real lexicon namespace" below: outside the local stack, nothing can sign in with the `space:` scope until the NSID authority resolves.
- **Hard rule still applies.** Client code can't use an atproto SDK, so the Spaces XRPC calls stay hand-written in `core/atproto`.

## AppView as a consented member for aggregation

Segments, leaderboards and similar features will need the AppView to read some athlete data that lives in spaces (age group, weight class, efforts). The AppView already reads every tier space as a member to serve non-public activities (`SpaceSyncer`, its own `APPVIEW_SIGNING_KEY`), and indexes only `feed.activity`, `feed.activityStreams` and `feed.activityZones` from them, and `feed.like` from `interactions` spaces (`Indexer.spaceCollections`), never `actor.privateDetails`.

- **Consent, not just a code path.** "Only me" means no other person sees a detail, but a 40–49 leaderboard still reveals a bracket. Index a detail only for athletes who opted into the feature that needs it (as Strava does for age and weight leaderboards), and only from the tiers it needs.
- **Scope it with the feature.** Design this alongside segments; nothing needs it yet.

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

## Keep more of what activity files hold

FIT, GPX, TCX, Shearwater dive XML, and UDDF and Subsurface logbook import (optionally gzipped) works on every client (`:core-activityfile`, `ImportActivityScreen`). Next:

- **Ride data.** Time in each gear and the longest jump's hang time and distance show on the import screen (`ImportedActivity.shifting`, `mtb`), but records don't carry them. Power, cadence, calories, shift counts, jumps, Grit and Flow publish on the activity, training load on its zones record, and laps in its streams.
- **Gear teeth.** FIT gear changes carry teeth from the device's gearing setup, which is often a default: an Edge 840 with SRAM AXS on a 10–52T Eagle bike reports a 39T ring and 10–26T cassette. The import screen shows gear numbers only. Showing teeth needs the athlete's own gearing, for example on a bike in a future gear list.
- **Left out of `store-dive-gas-switches`.** Device alerts, dive computer settings beyond water type, swim lengths, HRV, laps in TCX and GPX exports, and the extra dive data in Shearwater and Subsurface files (only their switch times are in scope). Planned in `keep-remaining-file-data`.
- **Duplicates beyond dives.** Imports flag duplicates of dives only (`DiveMatcher`); runs and rides imported twice (a watch and a bike computer on one ride) could use the same check and the same merge or group actions. A logbook dive twice in the file can't be merged into the other yet, only imported, grouped or ignored.
- **Bulk import.** Strava's account export is a zip of gzipped files plus `activities.csv` with titles. Reading the zip and importing many activities at once would reuse `ActivityFileReader`.
- **Track blob MIME type.** The local PDS stores the GPX blob of a `feed.activityTrack` as `application/xml`, not the lexicon's `application/gpx+xml`. Check whether a stricter PDS rejects it.

## Before deploying anywhere shared (QA, staging, prod)

Checks that only matter once the AppView runs on a database other than the local Docker one.

- **Search needs `pg_trgm`.** The PostgreSQL-only migration `db/vendor/postgresql/V6__actor_search_trigram_indexes.sql` runs `CREATE EXTENSION IF NOT EXISTS pg_trgm`. Since PostgreSQL 13 it's a trusted extension, so a role that owns the database (or has `CREATE` on it) can create it. That's untested, though: the local `appview` role is a superuser. Check the role on each target, or have an admin create the extension first so the migration's `IF NOT EXISTS` skips it. If it fails, Flyway stops and the AppView doesn't start.
- **Check the search plan at realistic size.** Once `actors` has a realistic number of rows, run `EXPLAIN ANALYZE` on `actor.searchActors`. Trigram indexes serve queries of three or more characters. A one- or two-character query scans `actors`, and deep pages cost more because paging is by offset. If that's too slow, require three characters, or match only handle and name prefixes for shorter queries.
- **Spaces need a real lexicon namespace.** A PDS grants the `space:` OAuth scope only if it can resolve the space type's lexicon declaration. Locally, `PDS_LEXICON_AUTHORITY_DID` points the PDS at `lexicons.test`, which publishes it (`docker/scripts/publish-lexicons.sh`, run by `scripts/dev.sh`). Anywhere else, the NSID authority must resolve through DNS: a `_lexicon` TXT record on the reversed namespace domain pointing at the DID that publishes the `com.atproto.lexicon.schema` records. `Nsid.NAMESPACE` (`app.activitytracker`) is a placeholder, so pick a domain we control first, then rename the namespace across `Nsid`, `lexicons/` and `create-account.sh`.
- **The AppView database holds non-public data.** It syncs athletes' tier spaces and stores their Followers, Mutuals and Only-me activities and streams (and caches their stream data), and their likes and comments on non-public activities. Treat the database and its backups as private: encrypt them at rest, restrict who can read them, and say so in the privacy policy.
- **`APPVIEW_SIGNING_KEY` is a secret.** It's the AppView's identity for reading athletes' spaces. Keep it in the deployment's secret store, not `docker/.env`. Rotating it means publishing the new key in `did.json`; PDSes cache DID documents, so syncs fail (and back off) until they refresh.

## More training zone options

Each sport can override the default thresholds (`actor.defs#sportTraining`, `SportZonesScreen`). Run and swim have Friel pace zones; everything else uses heart rate and power. Next:

- **Custom zones.** Let athletes edit zone boundaries and names, as TrainingPeaks does. Store them as explicit boundary arrays (say `heartRateZones`, `powerZones`, `speedZones`) on `#training` and `#sportTraining`, plus a `custom` model token. `Zones.compute` would then read boundaries from the record instead of a `ZoneModel`.
- **Threshold from a test.** Work out threshold pace or power from a test: distance plus time or pace (a 30-minute run, a CSS 400/200 swim), or a 20-minute power test.
- **More pace models.** 80/20, USAT, PZI, CTS and MyProCoach, each from a sourced, published formula. Row pace per 500 m needs a model before `SportZones.kinds` offers it.
- **Zones for recorded activities.** Only imports snapshot the athlete's zones (`importDraft` → `ZoneSnapshots`, then a `feed.activityZones` record). An activity recorded in the app has none, so its charts show no time in zone. Snapshot the training when a recording is saved, as imports do.

## Videos on activities

Activities carry photos (`feed.activity#media`, an open union with `#image` only). Videos join it as a `#video` member, and the code is laid out for that: `ActivityMedia`/`ActivityMediaView` decode an unknown member as `Unknown` and write it back untouched, `MediaItem` (`:app-screens`) gets a `Video` case whose `when`s then fail to compile wherever they need handling (`MediaRow`, the gallery, the details form), and the AppView's `/img/media/{did}/{cid}` redirect serves any blob an indexed activity lists.

- **Lexicon.** `#video`: a `video/mp4` blob of at most about 50 MB, `aspectRatio`, an optional `thumb` image blob for the feed, and `alt`. The view gets `#videoView` with the video's and the thumbnail's URLs.
- **Compression.** Phones re-encode to 720p H.264/AAC before upload: Media3 Transformer on Android, `AVAssetExportSession` on iOS. Desktop and web have no encoder, so they upload only a file already under the cap and refuse a larger one.
- **Playback.** `ComposeMediaPlayer` (`io.github.kdroidfilter:composemediaplayer`) covers Android, iOS, desktop and web. The feed row shows the poster frame with a play badge, and the gallery plays it.
- **Uploads.** A video is too big to hold as a `ByteArray` in memory or in the Room outbox. Upload from a file path, streamed, and let the outbox carry several file-backed blobs per write, so a video posts even after the app restarts.
- **PDS limit.** The reference PDS caps blobs with `PDS_BLOB_UPLOAD_LIMIT`; raise it in `docker/compose.yaml` and expect other PDSes to refuse large videos with a clear error.

## Spaces and route privacy: order of the open changes

Four OpenSpec changes in `openspec/changes/` cover file export, building on the archived `export-activity-files` and `export-fit-files` (GPX, TCX and FIT export, `feed/activity-export`). Implement them in this order, and delete each entry when its change is archived.

1. `export-dives-as-uddf`: dives also export as UDDF for logbook software, with summary, gases, samples and site.
2. `keep-original-activity-files`: imports keep the original file in a `feed.activitySource` in `self`, for "Download original". Reuses export's file hand-off (`rememberFileHandOff`).
3. `store-dive-gas-switches`: dives keep when each gas was switched to (a `gasSwitches` stream channel), mark switches on the depth chart, and FIT (and UDDF) exports write them, so a re-imported FIT keeps every gas. It also keeps the rest of what FIT and UDDF files hold that we drop: dive deco state and tank pressure over time, more dive summary, power, cadence and calories, training load (on the zones record), running dynamics and laps. A lexicon change.
4. `keep-remaining-file-data`: what 3 leaves out: device alerts, dive computer settings, swim lengths, HRV, TCX and GPX laps, and Shearwater and Subsurface deco state. A lexicon change. After 3.

## Loose ends from `store-dive-gas-switches`

Choices made while implementing the change that its tasks didn't settle. Each one works as is; decide whether to keep it.

- **Logbook notes are dropped.** A single file's notes start the import screen's description, but in a logbook batch they're dropped rather than published, because the athlete never sees them before publishing. To keep them, show each dive's notes in the batch list or let the athlete edit them there.
- **Training load rides on `getActivityStreams`.** The AppView serves the zones record's training load as a new `trainingLoad` field on the `getActivityStreams` output (only to viewers the zones record admits), which the change's tasks didn't name. The lexicon has it; add it to the change's spec, or move it to its own query if zones get one.
- **Gas-switch markers are on the detail depth chart only.** They're drawn on the activity page's depth chart; the import screen's preview (`ui/DepthProfile.kt`, which task 7.1 named) has none. Add them there if the preview should show switches before publishing.
