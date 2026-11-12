# Spec Delta

## ADDED Requirements

### Requirement: The Search tab reads the same in both UIs
The Search tab's title for each mode, the modes' names, the prompt shown while the query is blank and the message when nothing matched
(naming the trimmed query) SHALL be chosen once in shared code and resolved by each UI.

#### Scenario: A blank query
- **WHEN** the Search tab is in Athletes mode with nothing typed
- **THEN** both UIs show the prompt and no empty message

#### Scenario: No match
- **WHEN** the query is "  zzz " and nothing matched
- **THEN** both UIs read No athletes match “zzz”.

### Requirement: The left-elsewhere choice reads the same in both UIs
What the screen after leaving on another device says SHALL be chosen once in shared code and resolved by each UI: the intro naming the
handle, and a line for each kind of work this device holds (the changes counted, an import saving, an export not saved, a recording),
only for those it holds. Counts SHALL be grouped as `ui/number-format` says.

#### Scenario: Everything pending
- **WHEN** bob's device holds 1,200 changes, an import saving, an export and a recording
- **THEN** both UIs list "1,200 changes are pending." and the import, export and recording lines, in that order
