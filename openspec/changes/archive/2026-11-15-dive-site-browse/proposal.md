# Proposal

## Why

A dive site can be opened today only from a dive matched to it, or from a post that shares one. The segment importer has just published hundreds of OpenStreetMap dive sites (the Red Sea coast first), and athletes have no way to see any of them until somebody logs a dive there. The AppView already serves sites by area (`feed.searchDiveSites`), but no client calls it, and nothing finds a site by name.

## What Changes

- The Search tab's switch gains a third mode: **Athletes / Segments / Dive sites**. Dive sites mode is a new browse screen built like segment browse.
- **Map browse** (Android, iOS, desktop): the map draws each site in the visible area as its outline (centre and radius), and the list lists them nearest the map's centre first, with how far away each is. It opens where segment browse opens (the athlete's location, else their latest activity's start, else a prompt), asks to zoom in past 1 degree, and tapping an outline selects the site in the list; tapping a row opens the site's page.
- **Name search**: typing a name lists the sites whose current name matches, best match first and then nearest, with the same **This area / Everywhere** scope as segments. On web, without a map, it is name search and a list only, always Everywhere.
- New AppView query **`feed.searchDiveSiteNames`** (`q`, optional box, optional centre, cursor), serving exactly the sites `feed.searchDiveSites` would (active to everyone, proposed only to their creator and divers, never duplicate, archived, hidden or taken down), matched on the current name.
- Additive lexicon change only.

Placement and the new query were confirmed with the user: a separate Dive sites mode in the Search tab (not mixed into the segment map), with a server-side name search.

## Capabilities

### New Capabilities
- `dive-sites/site-browse`: the dive site browse and search screen: its place in the Search tab, the map and list, where it opens, the zoom limit, name search and its scope, web without a map, failures, and the native iOS view.

### Modified Capabilities
- `dive-sites/site-records`: sites can be found by name with `feed.searchDiveSiteNames`, with the area search's visibility rules.
- `segments/segment-browse`: the Search tab's mode switch has three modes, and the native iOS Search tab titles and offers the new one.

## Impact

- **Lexicons**: new `lexicons/app/activitytracker/feed/searchDiveSiteNames.json`; mirrored in `core/lexicon` (`Queries.kt`, `Nsid`).
- **AppView**: `DiveSiteViews.searchNames`, its route, and tests. No migration: it reads `dive_sites.current_name` and the centre.
- **Clients**: `DiveSiteRepository` gains the area and name reads; `DiveSiteBrowseScreen` in `feature/divesite/{api,presenter,ui}` and its samples in `:app-fixtures`; the Search tab's third mode (`feature/search` opens it through `:feature-divesite-api`); the iOS `DiveSiteBrowseView` over the same presenter. The map reuses `SegmentsMap`, drawing each site's outline as a closed line, and the browse start and viewport logic moves to `:app-common-presenter` (`MapBrowse`), shared with segment browse. New strings in `:ui-resources`.
- **Out of scope**: drawing sites on the segment map, clustering sites at low zoom, place or address search, and a real map on web.
