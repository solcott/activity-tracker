# Spec Delta

## ADDED Requirements

### Requirement: The native type sheet searches and collapses
On iOS the feed filter's native type sheet SHALL follow the type picker's rules: sports with sub-sports under them collapse and start collapsed, sub-sports filed elsewhere are top-level rows, and its search field (empty each time it opens) narrows the list with the shared match rule, showing every match, hiding the recent and common types and categories with no match, saying so when nothing matches, and leaving category headers unmarked and unselectable while a query is entered.

#### Scenario: Searching on iOS
- **WHEN** the athlete opens the native type sheet and types "trail"
- **THEN** Trail Run shows under Running without expanding Run, the quick types are hidden, and the Running header shows no mark

#### Scenario: Collapsed on iOS
- **WHEN** the athlete opens the native type sheet
- **THEN** Ride shows collapsed with its mark, and expanding it shows its sub-sports without changing the draft
