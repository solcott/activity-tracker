# Spec Delta

## ADDED Requirements

### Requirement: A page in a column has a header of its own
A native page drawn in a column beside another screen, under a navigation bar that belongs to that screen, SHALL show its title in a header at the top of its column, centred over the column, in the weight of a bar's title. Content SHALL scroll under it as it scrolls under the system's bar, in light and dark, on every supported iOS version.

#### Scenario: A page beside a list
- **WHEN** a native page titled "Segments & groups" is drawn beside a list
- **THEN** "Segments & groups" is centred at the top of the page's column, below the bar over both columns

#### Scenario: Scrolled content
- **WHEN** the page in the column is scrolled
- **THEN** its rows pass under the header, which stays readable over them
