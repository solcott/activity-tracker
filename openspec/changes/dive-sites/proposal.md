# Proposal

## Why

A dive is tied to a place more than any other activity, but the app keeps only a free-text `dive.site` name and a private entry position. Divers can't see who else dives a site, what conditions are usually like there, or their own history at it, and every logbook spells the same reef differently. Segments already solve the same problem for trails: public places, private matching under consent, statuses, boards and local naming. Dive sites can reuse that model, with a point where segments have a line.

## What Changes

- Add a public **dive site** record, `feed.diveSite`, in its creator's repo. It holds a name, a centre point and a radius, its source and licence. The AppView indexes it, derives a status (proposed, active, duplicate, archived) the way it does for segments, and serves site search by area.
- **Visits:** for athletes with segment matching on, the AppView matches each dive's private entry position (from its segment track) to the nearest live site whose radius contains it. A visit is served only at the narrowest of the activity's and its Map category's visibility.
- **Athlete-created sites:** on their own dive, an athlete can publish a site at the dive's entry position (outside their privacy zones), after a duplicate check and within a daily allowance.
- **Imported sites:** the segment importer extracts named OSM dive sites and publishes those that duplicate no live site, as the importer account.
- **Site screen:** the site on a map with its name and status, the dives there that the viewer may see, conditions (water temperature, visibility and maximum depth, from dives whose Dive category the viewer may see), a Regulars board of who dived it most, and local naming.
- **Names:** sites are renamed by proposal and vote under the segment naming rules. This reuses `feed.segmentNameProposal` and `feed.segmentNameVote` with a dive site as the subject, so the `interactions` space declaration doesn't change and nobody needs to sign in again.
- The dive's activity page links its matched site, by the site's current name.

## Capabilities

### New Capabilities
- `dive-sites/site-records`: the `feed.diveSite` record, its indexing and validation, statuses, duplicates, the daily allowance and search by area.
- `dive-sites/site-visits`: matching dives to sites under segment-matching consent, visit visibility, listing a site's dives, and the dive page's site link.
- `dive-sites/site-creation`: publishing a site from one's own dive in the app.
- `dive-sites/site-import`: extracting OSM dive sites and publishing them as the importer account.
- `dive-sites/site-conditions`: a site's water temperature, visibility and depth, aggregated over the dives the viewer may see.
- `dive-sites/site-regulars`: the board of athletes with the most visits to a site.
- `dive-sites/site-names`: renaming sites by proposal and vote.

### Modified Capabilities
- `segments/segment-names`: a name proposal may name a dive site as well as a segment, and votes on such proposals follow `dive-sites/site-names`.

## Impact

- **Lexicons:** new `feed/diveSite.json`, `feed/getDiveSite.json`, `feed/searchDiveSites.json`, `feed/checkDiveSite.json`, `feed/getDiveSiteDives.json`, `feed/getDiveSiteConditions.json`, `feed/getDiveSiteRegulars.json`, `importer/checkDiveSites.json`, plus `diveSiteView` / `diveSiteViewBasic` in `feed/defs.json`. `activityView` gains an optional `diveSite`, and `segmentNameProposal`'s subject description changes. All are mirrored in `core/lexicon`, with `Nsid.indexedCollections` gaining `feed.diveSite`.
- **AppView:** a Flyway migration (sites, site cells, visits, and the site name tables or a subject-kind column on the existing ones), an indexer, a `site` matcher job alongside the `track` job, views and routes.
- **Segment importer:** an `extract-sites` / `publish-sites` pair and `scripts/segment-import.sh` options.
- **Clients:** a new `feature/divesite` (`-api`, `-presenter`, `-ui`) for the site screen, create-site and site naming. Also a site row on the dive's activity page (`feature/detail`), strings, fixtures and screenshot references.
- **Out of scope:** a map for browsing sites, dive-site leaderboards by depth or time, site photos, linking a dive to a site by hand, and snorkel or pool sessions.
