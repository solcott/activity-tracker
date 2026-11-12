# Spec Delta

## ADDED Requirements

### Requirement: The Search tab is native on iOS
On iOS the Search tab's root SHALL be drawn with the system's navigation bar under a large title ("Find athletes" in Athletes mode,
"Search" in Segments mode), with Athletes and Segments as a segmented choice under the bar.

#### Scenario: Switching mode
- **WHEN** alice chooses Segments on the native Search tab
- **THEN** the title reads "Search" and the choice shows Segments

### Requirement: Native athlete search uses the system's search field
On iOS, Athletes mode SHALL search with the system's search field and list the matching accounts with their picture, name and handle,
each opening its profile, loaded page by page as `ui/content-loading` says. It SHALL show the prompt while the query is blank and say
that nothing matched when nothing did.

#### Scenario: Finding an athlete
- **WHEN** alice types "bo" into the native Search tab's search field
- **THEN** once typing pauses it lists Bob with his picture, name and @handle, and tapping him opens his profile on the Search tab

#### Scenario: Nothing matches
- **WHEN** alice searches "zzz" and no athlete matches
- **THEN** the native Search tab says No athletes match “zzz”.

### Requirement: Native Segments mode shows the shared browse screen
On iOS, until segment browse is native, Segments mode SHALL show the shared segment browse screen under the native choice, opening what
it opens on the Search tab and keeping its query and map while the tab's root stays.

#### Scenario: Opening a segment
- **WHEN** alice taps a segment in the browse list under the native choice
- **THEN** its page opens on the Search tab, and back returns to Segments mode as she left it
