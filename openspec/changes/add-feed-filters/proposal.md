# Proposal

## Why

Right now the feed shows every activity from the athlete and everyone they follow, newest first, and it can't be narrowed. Athletes want two things:
- **Scope**: switch between "Following" (follows plus their own) and "Mine".
- **Narrowing**: limit the feed by sport, sub-sport and date range.

This is the first of several feed filters. The profile screen will reuse the same filter control, so we're building it as a reusable component now instead of wiring it into the feed screen alone.

## What Changes

- **Feed scope.** The feed gets a Following/Mine switch.
  - Following keeps today's behavior: follows plus the viewer's own activities.
  - Mine shows only the viewer's own activities.
- **Activity type filter.** Multi-select across `SportCatalog`:
  - Picking a whole category selects all its sports, including category shortcuts.
  - Picking a sport matches all its sub-sports.
  - Picking a single `sport:subSport` matches only that.
  - A sport with every sub-sport selected collapses to the sport.
- **Date range filter.** Presets (last 7 days, last 30 days, this year) and a custom range, matched on when the activity *started*.
- **New query parameters.** `feed.getTimeline` and `feed.getAuthorActivities` get optional `type[]`, `since` and `until` parameters, and the AppView applies them server-side.
  - They're backward compatible: omitting them returns today's results.
  - Filtering runs on the server because the client caches only the first page.
- **Separate caches.** The client cache keeps filtered feeds apart from the unfiltered feed and from each other.
- **Reusable SubCircuit.** The filter control is a Circuit SubCircuit, built on the app-wide SubCircuit setup from `setup-subcircuit`.
- **Out of scope:**
  - Embedding the filter on the profile screen.
  - Persisting the filter across app launches.
  - Showing outbox (not-yet-indexed) activities in filtered feeds.
  - Any new database index.

## Capabilities

### New Capabilities
- `feed/activity-query-filters`: optional type and start-date filters on the AppView's timeline and author-activities queries, including validation and paging under a filter.
- `feed/feed-filter`: the athlete-facing feed filter, covering scope (Following/Mine), activity-type multi-select, date range, how the feed reacts to a change, and caching of filtered results.

### Modified Capabilities
<!-- None: no specs exist yet. -->

## Impact

- **Lexicons:** `app.activitytracker.feed.getTimeline` and `app.activitytracker.feed.getAuthorActivities` gain optional params. They are mirrored in `core/lexicon` (`GetTimeline.Params`, `GetAuthorActivities.Params`).
- **AppView:** `Views.timeline`/`authorActivities` build extra predicates for `activityPage`. Tests are added. No migration.
- **Client data:** `FeedRepository` gains filter-aware `timeline`/`authorActivities`/refresh methods and filter-derived Room cache keys.
- **Client UI:**
  - New contracts in `:app-screens`: `FeedFilterScreen`, state and events.
  - `FeedFilterPresenter` in `:app-presenters`.
  - `FeedFilterUi` in `composeApp`.
  - `FeedScreen`, `FeedPresenter` and `FeedUi` change to embed the filter.
- **Depends on `setup-subcircuit`**, which adds the SubCircuit dependencies, the session `SubCircuit` binding, `LocalSubCircuit` and the SubCircuit conventions. Apply and archive it first.
