# Spec Delta

## ADDED Requirements

### Requirement: The combine picker is native on iOS
On iOS the combine picker SHALL be a native list under the system's bar: the explanation, then each candidate with its type's icon, title, start and length, and device, a tap combining it with a spinner on that row while the others wait, why combining failed, and a message when there is none to combine with; its loading and failures as `ui/content-loading` says.

#### Scenario: Picking
- **WHEN** an athlete on iOS taps a candidate in the combine picker
- **THEN** that row shows a spinner, the other rows can't be tapped, and the two activities are combined

#### Scenario: Nothing to combine
- **WHEN** the athlete has no other activity within 12 hours
- **THEN** the picker says there is none to combine with
