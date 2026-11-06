# Proposal

## Why

An athlete can take one activity out at a time (feed/activity-export), and their PDS can export their repo as a CAR file (`com.atproto.sync.getRepo`). Neither gets them everything: the CAR leaves out every athlete space, which holds their non-public activities and their details and streams, every full-resolution track, original file and device list, their privacy zones, private profile details, activity defaults, segment and grouping choices, most of their gear, and their likes, comments, blocks, mutes and votes. Export is the second of the account-management changes. It comes before "leave Activity Tracker" because it's the safety net for that destructive step: an athlete should be able to keep a copy of everything the app holds of theirs before deleting it.

## What Changes

- New **Export your data** row on the Account page (auth/account-page). It starts a whole-account export, shows its progress, and offers the finished file.
- The export is a single zip holding:
  - every `app.activitytracker.*` record the athlete owns in their repo and in each of their athlete spaces (followers, mutuals, self, interactions), as JSON exactly as stored, with the blobs those records name;
  - one ready-to-open file per activity: the original file it was imported from when there is one, else a FIT rebuilt by the existing per-activity exporter, else nothing (a manual entry with no track or streams);
  - a manifest (account, time, counts, what's missing and why) and a README saying what's in the archive and what isn't.
- Everything is read straight from the athlete's PDS. The export doesn't depend on the AppView.
- The export is a persisted, account-scoped job, like batch import saves. It keeps running when the Account page closes, runs as a foreground job on Android and in a background task on iOS, and after the process is killed it resumes where it left off. On desktop and the web it runs while the app is open.
- When the file is ready, the athlete saves it: a save dialog on Android and desktop, the share sheet (with Save to Files) on iOS, a download on the web. The app deletes its copy once it's saved, when it's 7 days old, or when the account signs out.
- An item that can't be read after retries doesn't fail the export. The archive lists it in the manifest, and the Account page says the export finished with problems.
- Signing out (or Sign in again) while an export runs asks first, as it does for pending uploads, then cancels the export and deletes its files.
- New dependency: `kmp-zip` (with its kotlinx-io adapter) for streaming ZIP64 archives on every target.

Out of scope: importing or restoring an archive; data the AppView derives (segment efforts, leaderboard places, group pairs, segment statuses); records of other apps (Bluesky posts, `app.bsky.graph.block`); the PDS's own account export (CAR file, preferences); and export of other athletes' data.

## Capabilities

### New Capabilities
- `auth/data-export`: the whole-account export: what the archive holds and how it's laid out, reading from the repo and every athlete space, the per-activity files, the persisted resumable job and its progress, problems recorded rather than fatal, handing the file to the platform, and cleaning up the app's copy.

### Modified Capabilities
- `auth/account-page`: the Account page gains the Export row and its progress, and Sign out (and Sign in again) also warns about an export still running and cancels it.

## Impact

- `core/data`: a new `DataExporter` (walk the repo and spaces, stage records and blobs, build per-activity files), its Room tables and DAO for the job and its staged items (a new Room schema version), an `ActivityExporter` entry point that builds from a `StoredActivity` read off the PDS, and `AuthRepository`'s pending-work summary.
- `core/atproto`: repo `listRecords` and `getBlob` paging for the athlete's own repo, if not already exposed beside `SpaceClient`.
- `feature/settings` (`-api`, `-presenter`, `-ui`), `:app-fixtures` samples, Settings UI tests and `:androidApp` screenshot references.
- Platform pieces: an Android WorkManager worker and notification, an iOS background processing task identifier in `Info.plist`, desktop save-dialog hand-off, and a wasm OPFS sink and download.
- `app/common/ui`: hand-off for a file on disk, not just a `ByteArray`.
- `:ui-resources` strings. `gradle/libs.versions.toml`: `no.synth:kmp-zip`, `no.synth:kmp-zip-kotlinx`, `kotlinx-serialization-json-io`. No lexicon, AppView or Flyway migration changes.
