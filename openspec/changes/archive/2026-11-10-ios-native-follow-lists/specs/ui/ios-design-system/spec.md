# Spec Delta

## ADDED Requirements

### Requirement: A page's title can say whose page it is
A native page's title SHALL be able to carry a second, smaller line under it, such as whose list the page is. In the stack's bar it SHALL show as the system shows a navigation subtitle, on every supported iOS version, and in a column header as a second line under the title. VoiceOver SHALL read both.

#### Scenario: A list with a subtitle
- **WHEN** alice's Followers list is shown in its stack
- **THEN** the bar reads "Followers" with "@alice.test" under it, smaller and secondary

#### Scenario: No subtitle yet
- **WHEN** the page's subtitle isn't known yet
- **THEN** the bar shows the title alone, at its usual place
