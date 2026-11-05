# dive-sites/site-visits Specification

## Purpose

How dives are matched to dive sites from athletes' private segment tracks, who may see a visit, and how visits appear on a dive's page and a site's list of dives.

## Requirements

### Requirement: Visits come only from usable segment tracks
The AppView SHALL match a dive to sites only from its athlete's usable segment track, only while that athlete's segment matching is on, and only for activities of sport `dive` that aren't pool or indoor sessions. It SHALL NOT read positions from anywhere else.

#### Scenario: Matching off
- **WHEN** an athlete with segment matching off imports a dive at an active site
- **THEN** the dive gets no visit

#### Scenario: Pool session
- **WHEN** an athlete logs a pool apnea session inside an active site's radius
- **THEN** it gets no visit

### Requirement: A dive visits the nearest site containing its entry
A dive SHALL visit the one `proposed` or `active` site whose centre is nearest its entry position, among those whose radius contains it. The entry position SHALL be the first position of its segment track. A dive SHALL have at most one visit.

#### Scenario: Entry inside two sites
- **WHEN** a dive's entry lies within both a 150 m site 120 m away and a 100 m site 40 m away
- **THEN** it visits the site 40 m away

#### Scenario: Entry outside every site
- **WHEN** a dive's entry is 600 m from the nearest site
- **THEN** it has no visit

### Requirement: A visit is visible at its narrowest tier
A visit SHALL be visible at the narrowest of the activity's visibility and its Map category's. The AppView SHALL serve nothing about a visit, its existence included, to anyone else, and SHALL re-evaluate when either changes.

#### Scenario: Map hidden
- **WHEN** a Public dive's Map category is Only me
- **THEN** only the athlete sees its visit

### Requirement: Visits follow their sources
Visits SHALL be recomputed when a dive's segment track, the dive, or a site changes, and a new site SHALL be matched against existing usable tracks near it. Deleting the dive, its segment track or the site, turning matching off, deleting the `self` space or the account SHALL remove the visits that came from it. Members of a combined activity SHALL get no visits; the combined activity SHALL.

#### Scenario: New site, old dives
- **WHEN** a site is published where an athlete dived last year with matching on
- **THEN** that dive gains a visit to the new site

### Requirement: A dive's page links its site
`feed.getActivity` SHALL give a dive's visited site, as a brief view with its current name and status, when the viewer may see the visit. The dive's page SHALL show it and open the site's screen when tapped. Without a visible visit it SHALL show the record's own site name, if any, as text.

#### Scenario: Dive at an active site
- **WHEN** a viewer opens a dive whose visit they may see
- **THEN** the page shows the site's current name and opens the site when tapped

#### Scenario: Visit hidden from viewer
- **WHEN** a follower opens a dive whose Map category is Only me
- **THEN** the page shows only the dive's own site text, with no link

### Requirement: A site lists its dives
`feed.getDiveSiteDives` SHALL list a site's visits that the viewer may see, newest first, at most 50 per page with a cursor, each with the dive's activity view. The site screen SHALL show them, opening a dive when tapped.

#### Scenario: Site with private dives
- **WHEN** a site has five visits, two of them from Only me dives by other athletes
- **THEN** a signed-out viewer is listed only the Public ones
