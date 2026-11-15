# Spec Delta

## MODIFIED Requirements

### Requirement: Segments mode in the Search tab
The Search tab SHALL offer a switch between Athletes, Segments and Dive sites. Athletes SHALL be the athlete search it shows today, and SHALL be selected the first time the tab opens. Segments SHALL show the segment browse screen, and Dive sites the dive site browse screen (`dive-sites/site-browse`). The tab SHALL keep the chosen mode, and each mode's query and results, while the athlete visits other tabs and comes back.

#### Scenario: Switching to segments
- **WHEN** an athlete on the Search tab chooses Segments
- **THEN** the tab shows the segment browse screen in place of athlete search

#### Scenario: Coming back to the tab
- **WHEN** an athlete in Segments mode goes to the Feed tab and back to Search
- **THEN** Segments mode is still selected, with the same map area, query and results

### Requirement: The Search tab is native on iOS
On iOS the Search tab's root SHALL be drawn with the system's navigation bar under a large title ("Find athletes" in Athletes mode,
"Search" in Segments and Dive sites modes), with Athletes, Segments and Dive sites as a segmented choice under the bar.

#### Scenario: Switching mode
- **WHEN** alice chooses Segments on the native Search tab
- **THEN** the title reads "Search" and the choice shows Segments
