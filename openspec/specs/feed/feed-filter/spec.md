# feed/feed-filter Specification

## Purpose

Lets athletes narrow what the feed shows by whose activities they see (Following or Mine), what kind of activity it is, and when it happened. The control is reusable wherever a list of activities can be filtered.

## Requirements

### Requirement: Feed scope
The feed SHALL offer two scopes, picked from the feed's title so that more (such as clubs) can join them. Following shows activities from athletes the viewer follows plus the viewer's own. Mine shows only the viewer's own. The feed SHALL open on Following with no other filter, which matches today's feed.

#### Scenario: Default feed
- **WHEN** the athlete opens the feed
- **THEN** the Following scope is selected, no type or date filter is active, and the feed shows the same activities as before this change

#### Scenario: Switch to Mine
- **WHEN** the athlete selects Mine
- **THEN** the feed shows only their own activities

#### Scenario: Following includes own activities
- **WHEN** the Following scope is selected
- **THEN** the athlete's own activities appear alongside those of athletes they follow

### Requirement: Activity type multi-select
The athlete SHALL be able to select any number of activity types from the sport catalog: whole categories, sports, or individual sport and sub-sport pairs. The feed SHALL show activities matching any selected type. An empty selection SHALL mean all types.

#### Scenario: Select a sport
- **WHEN** the athlete selects Run
- **THEN** the feed shows runs of every sub-sport (road, trail, treadmill and so on)

#### Scenario: Select one sub-sport
- **WHEN** the athlete selects only Trail Run
- **THEN** the feed shows only trail runs

#### Scenario: Select a category
- **WHEN** the athlete selects a whole category such as Cycling
- **THEN** every sport and shortcut type in that category is selected and the feed shows activities of any of them

#### Scenario: Full selection collapses
- **WHEN** the athlete individually selects every sub-sport of a sport
- **THEN** the selection is treated as the sport itself, so its generic form also matches

### Requirement: Type selections apply on confirm
Type selections SHALL be made in a picker as a draft and SHALL change the feed only when the athlete applies them. Dismissing the picker SHALL discard the draft.

#### Scenario: Dismiss discards changes
- **WHEN** the athlete toggles types in the picker and then dismisses it
- **THEN** the feed and the active filter are unchanged

#### Scenario: Apply updates the feed
- **WHEN** the athlete toggles types and applies
- **THEN** the feed reloads with the new type filter

### Requirement: Date range filter
The athlete SHALL be able to limit the feed to activities that started in a date range, chosen from presets (last 7 days, last 30 days, this year) or as a custom start and end date. Range boundaries SHALL use the athlete's local calendar days, and the end date SHALL be inclusive.

#### Scenario: Preset range
- **WHEN** the athlete picks "Last 7 days"
- **THEN** the feed shows only activities that started within the last 7 local days, including today

#### Scenario: Custom range
- **WHEN** the athlete picks a custom range from March 1 to March 31
- **THEN** the feed shows activities that started on any local day from March 1 through March 31

### Requirement: Active filter summary and clearing
When any type or date filter is active, the feed SHALL show a summary of it and SHALL offer a single action that clears type and date filters while keeping the scope.

#### Scenario: Clear filters
- **WHEN** the athlete has the Mine scope with a type and a date filter and clears filters
- **THEN** the type and date filters are removed, Mine stays selected, and the feed reloads

### Requirement: Feed reacts to filter changes
When the filter changes, the feed SHALL show results for the new filter, fetched from the server, and SHALL NOT show activities from a previous filter as if they matched. Errors loading a filtered feed SHALL be reported as feed-loading errors are today.

#### Scenario: Change filter
- **WHEN** the athlete changes from no filter to Rides only
- **THEN** the feed shows only rides once loading completes, and never shows non-ride activities under the Rides filter

### Requirement: Filtered feeds are cached separately
Each distinct filter's first page SHALL be cached apart from the unfiltered feed and from other filters, so returning to a filter shows its last results immediately, offline included. Equivalent filters (the same selections in a different order) SHALL share one cache entry.

#### Scenario: Return to a previous filter offline
- **WHEN** the athlete views Rides only, switches back to no filter, goes offline, and selects Rides only again
- **THEN** the last-loaded rides are shown immediately

#### Scenario: Unfiltered cache untouched
- **WHEN** the athlete loads a filtered feed
- **THEN** the cached unfiltered feed is unchanged

### Requirement: Filter lasts for the session
The active filter SHALL survive navigating away from and back to the feed, and configuration changes, within a signed-in session. It SHALL reset to the default when the app restarts or the athlete signs out.

#### Scenario: Navigate away and back
- **WHEN** the athlete sets a filter, opens an activity, and goes back
- **THEN** the same filter is still active

#### Scenario: App restart
- **WHEN** the app is restarted
- **THEN** the feed opens on the default filter (Following, no type or date filter)
