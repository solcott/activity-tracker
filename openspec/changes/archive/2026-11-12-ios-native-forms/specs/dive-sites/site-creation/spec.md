# Spec Delta

## ADDED Requirements

### Requirement: Creating a site is native on iOS
On iOS creating a dive site SHALL be drawn natively: the site's outline on a map with no start or end marked, its radius with a slider, the name, what will be public, any duplicate sites to open, the allowance reached, a failed check or publish, and Publish in the bar, enabled only when a named site can be made and nothing is pending, with progress while publishing. When no site can be made from the dive it SHALL say why instead. Its loading SHALL follow `ui/content-loading`.

#### Scenario: Moving the radius
- **WHEN** alice drags the radius slider to 200 m
- **THEN** the label reads 200 m and the outline on the map grows

#### Scenario: Inside a privacy zone
- **WHEN** the dive's entry lies inside one of alice's privacy zones
- **THEN** the page says so and offers no form
