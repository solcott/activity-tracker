# Proposal

## Why

Segment state is derived by background passes (status, naming, elevation, matching) that run on their own schedule, and `segments/segment-pins` added operator pins with no way to call them: today an operator who wants a segment promoted, renamed, rematched or its elevation redone writes a Flyway migration or waits up to an hour. The admin site (`admin/admin-site`) now has the shell, roles, CSRF and audit log, so segments can get their page.

## What Changes

- A **Segments** admin page (`AdminPage`, path `segments`): search segments by AT-URI or rkey, by name, by status, by sport group and kind, and by importer region; each result shows its URI, served name, status, sport group, kind and author DID.
- A **segment page** (`segments/segment?uri=…`): the segment's identifiers, sport group, kind, distance, first-indexed and indexed times, original and current name, status and `duplicate_of`, its status and name pins (value, in effect or not, who pinned, when, note), its served elevation and whether terrain was sampled, its queued matcher jobs by kind, and two counts: efforts and distinct athletes with efforts, of every visibility, with the threshold that makes it active. No effort, track, athlete list or date.
- The segment's **name proposals**: URI, proposer DID, state, opened and closed times and up and down totals (live for open ones, final for closed). Never who voted. A proposal the AppView wouldn't list to a signed-out viewer shows no name.
- An **Importer** section: per region, each staged import revision with its candidate count, how many are ridden and not yet published, how many are published, and its latest staging time. Never a rider count, who rode, or when.
- **Pins on missing segments**: the pins `SegmentPins.orphanedPins` lists, with unpin.
- **Operator actions** on one segment, each an `AdminRoutes.action` (POST, operators only, CSRF, audited): run the status pass, run the naming pass, recompute elevation, rematch, pin status (`active`, `archived`, `duplicate` of a URI, duplicate needing an explicit confirmation since it removes efforts), unpin status, pin name (free text or one of its proposals), unpin name.
- A one-segment form of the status pass and of the naming pass, sharing the hourly passes' code so both apply the same rules.
- Actions can report a refusal (an invalid pin, nothing to unpin, a segment not live for a rematch) as their audited outcome instead of `ok`.

## Capabilities

### New Capabilities
- `admin/segment-tools`: the admin site's segment pages and operator actions: segment search, the segment page and what it may show, importer runs, proposals with totals, pins on missing segments, and the per-segment recompute, rematch, pin and unpin actions with their auditing.

### Modified Capabilities

None. The one-segment status and naming passes apply the rules `segments/segment-records` and `segments/segment-names` already state, and pins are set only through `segments/segment-pins`' service as that spec requires.

## Impact

- New `server/appview/.../admin/SegmentsPage.kt` (page, search, segment page, importer and orphaned pins sections, actions) and `admin/SegmentAdminQueries.kt` (column-limited reads).
- `segments/SegmentMatcher.kt` and `segments/SegmentNaming.kt`: the status and naming passes take an optional set of segment URIs, through a new `segments/SegmentPasses.kt` the matcher and the admin page share.
- `admin/AdminPage.kt` / `admin/AdminSite.kt`: `ActionResult` gains an outcome other than `ok`.
- `Application.kt`: passes `adminPages = listOf(SegmentsPage(…))` to `appView`, built from `indexer.segmentPins`, `indexer.segmentJobs` and the config.
- Tests: `AdminSegmentsTest` (Ktor test host), `SegmentPassesTest`, `AdminPrivacyTest`'s `pagesUnderTest`.
- `.agents/rules/appview.md`: one line on the segments page.
- No migration, no lexicon, no XRPC method, no client change.
