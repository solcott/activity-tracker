# Spec Delta

## ADDED Requirements

### Requirement: A filter shows its choice and opens a menu
A native filter over a list SHALL show the option chosen and, when tapped, a menu of every option with the chosen one checked. It SHALL look filled while its choice narrows the list and plain while it is the choice that shows everything, in light and dark. VoiceOver SHALL read its choice and that it opens a menu.

#### Scenario: A filter narrowing the list
- **WHEN** a board's age filter has "45–54" chosen
- **THEN** it reads "45–54", filled, and its menu has "45–54" checked

#### Scenario: A filter showing everything
- **WHEN** a board's gender filter has "All genders" chosen
- **THEN** it reads "All genders" and is not filled

### Requirement: Rows can follow a paged list's items
A native paged list SHALL be able to show rows of the screen's own after its items, such as the viewer's own place, above the empty message and the next page's progress or failure, as the shared board orders them.

#### Scenario: The viewer's place and the next page
- **WHEN** a board's first page is loaded, more can load, and the viewer isn't on that page
- **THEN** "Your place" with the viewer's entry shows under the entries, and the next page's progress below it
