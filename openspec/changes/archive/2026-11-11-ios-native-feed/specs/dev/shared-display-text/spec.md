# Spec Delta

## ADDED Requirements

### Requirement: The feed reads the same in both UIs
What the feed says SHALL be chosen once in shared code and resolved by each UI: its title and scopes, the Add menu's entries (Record only where recording isn't a button of its own and the device can record), the refused and uploading rows with the imports' and the server's pauses, and the empty message with whether it offers Clear filters. Counts SHALL be grouped as `ui/number-format` says.

#### Scenario: Uploading
- **WHEN** one activity, 3,000 imported ones and 3 other changes are uploading
- **THEN** both UIs read "Uploading 1 activity and 3,000 imported · 3 other changes"

#### Scenario: A device that can't record
- **WHEN** the feed is empty on a device without GPS recording
- **THEN** both UIs say to add one manually, and neither Add menu offers Record

### Requirement: An import card reads the same in both UIs
An import card's months, count, sports and distance, and which routes it draws, SHALL be chosen once in shared code and resolved by each UI.

#### Scenario: Bob's import
- **WHEN** both UIs show Bob's import of 312 activities from June 2019 to May 2026
- **THEN** each reads "Imported 312 activities" and "Jun 2019 – May 2026", with at most four routes
