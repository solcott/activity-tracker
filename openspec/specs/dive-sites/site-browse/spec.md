# dive-sites/site-browse Specification

## Purpose

Lets a signed-in athlete discover dive sites without first logging a dive at one: browse the sites around a place on a map and in a list, and find one by name, then open its site page.

## Requirements

### Requirement: Dive sites mode in the Search tab
The Search tab SHALL offer Dive sites beside Athletes and Segments. Dive sites SHALL show the dive site browse screen. The tab SHALL keep the chosen mode, and the screen's query, map area and results, while the athlete visits other tabs and comes back.

#### Scenario: Switching to dive sites
- **WHEN** an athlete on the Search tab chooses Dive sites
- **THEN** the tab shows the dive site browse screen

### Requirement: Browse shows sites on a map and in a list
On Android, iOS and desktop, the browse screen SHALL show a map the athlete can pan and zoom, north-up, and a list. With no name query, the map SHALL draw the outline (centre and radius) of every site whose centre lies in the visible area that `feed.searchDiveSites` serves the athlete, and the list SHALL list them nearest the map's centre first, each with its name and how far away its centre is. Both SHALL follow the area as the athlete moves the map. It SHALL open where segment browse opens (`segments/segment-browse`) and keep the area the athlete last looked at.

#### Scenario: A reef coast
- **WHEN** an athlete pans the map to a stretch of coast with three active sites
- **THEN** the map draws the three outlines and the list lists them, nearest the map's centre first

#### Scenario: Proposed site someone else made
- **WHEN** the visible area holds a proposed site the athlete didn't create and hasn't dived
- **THEN** neither the map nor the list shows it

### Requirement: Site browse needs a close enough view
When the visible area is wider than 1 degree on either side, the browse screen SHALL draw no sites, list none, and say to zoom in to see dive sites, without asking the AppView.

#### Scenario: Zoomed out to the Red Sea
- **WHEN** an athlete zooms out until the map shows the whole Red Sea
- **THEN** no outlines are drawn and the list says to zoom in to see dive sites

### Requirement: Site map and list share a selection
Tapping a site's outline on the map SHALL select it: the outline SHALL be highlighted and its row scrolled to and marked. Tapping the map away from every outline SHALL clear the selection. Tapping a row SHALL open the site's page, and back SHALL return to the browse screen as it was.

#### Scenario: Opening a site
- **WHEN** an athlete taps "SS Thistlegorm" in the list
- **THEN** its dive site page opens, and back returns to the browse screen with the same map and list

### Requirement: Sites can be found by name
The browse screen SHALL have a search box. A non-blank query SHALL list the sites whose current name matches it, best match first and then nearest the map's centre, a page at a time, and the map SHALL draw the matches. Where the map is shown the athlete SHALL choose This area or Everywhere, This area by default; when This area finds nothing the screen SHALL offer to search Everywhere. Clearing the query SHALL return to browsing the area. The query and results SHALL load as segment name search does (`ui/content-loading`).

#### Scenario: Searching a name
- **WHEN** an athlete types "thistle" with the map over the Strait of Gubal
- **THEN** the list shows "SS Thistlegorm"

#### Scenario: Nothing in this area
- **WHEN** an athlete searches "blue hole" in This area over Hurghada
- **THEN** the list says no dive site matches in this area and offers to search everywhere, which lists the Dahab site

### Requirement: Web lists sites without a map
The web app SHALL show the browse screen as the search box and the name results only, with no map and no scope choice: every search SHALL cover Everywhere, by match alone. A blank query SHALL show a prompt to search by name.

#### Scenario: Searching on web
- **WHEN** an athlete on the web app types "thistle" in Dive sites mode
- **THEN** matching sites from anywhere are listed, and tapping one opens its page

### Requirement: Site browse shows why sites couldn't load
When the sites for an area or a name query can't be loaded, the browse screen SHALL keep any sites it was showing, say why through the app's error messages, and offer to try again.

#### Scenario: Offline while panning
- **WHEN** an athlete pans the map while offline
- **THEN** the outlines already drawn stay, and the list says it couldn't load dive sites, with a way to try again

### Requirement: Site browse is native on iOS
On iOS the Search tab's Dive sites mode SHALL show site browse natively under the mode choice: the search box, the This area and Everywhere choice while a name is searched in an area, the outlines on the native map, and the list, beside the map on a wide window and under it on a phone, in the shared page's words. A tap on the map SHALL select the site whose outline is nearest within a finger's width. What it opens SHALL open on the Search tab.

#### Scenario: Browsing on a phone
- **WHEN** alice chooses Dive sites on an iPhone over Ras Mohammed
- **THEN** the native map shows the sites' outlines and the list under it shows each with how far away it is, opening its page on the Search tab
