# Spec Delta

## ADDED Requirements

### Requirement: The dive site page is native on iOS
On iOS a dive site's page SHALL be drawn with the system's own list and bar, titled with the site's current name, with its centre and radius on the native route map, its about lines, its Regulars card, its conditions and its names, in the shared page's words, its loading and failures as `ui/content-loading` says.

#### Scenario: An active site
- **WHEN** a viewer opens an active site with 31 visits by Bob and conditions from 12 dives
- **THEN** the page shows the map, the radius, the creator, the Regulars card with Bob opening the board, and the conditions

### Requirement: The native site page explains a site that isn't active
The native page SHALL show OpenStreetMap's credit, opening its copyright page, for an imported site and the data licence, and for a site that isn't active its status and what it means, with Open the original for a duplicate whose original exists.

#### Scenario: A proposed OpenStreetMap site
- **WHEN** a viewer opens a proposed site imported from OpenStreetMap
- **THEN** the page shows OpenStreetMap's credit, the licence and "Proposed" with what it means, and no Regulars card

#### Scenario: A duplicate
- **WHEN** a viewer opens a duplicate of an earlier site and taps Open the original
- **THEN** the earlier site's page opens over it

### Requirement: The native site page lists its dives as cards
The native page SHALL list the dives at the site the viewer may see as native activity cards, newest first, page by page per `ui/content-loading`, each opening its dive, with no author link and no Like, or say there are none.

#### Scenario: No dives
- **WHEN** the site has no dives the viewer may see
- **THEN** the page reads "No dives here that you can see yet"
