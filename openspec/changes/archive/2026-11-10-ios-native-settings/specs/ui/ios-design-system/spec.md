# Spec Delta

## MODIFIED Requirements

### Requirement: A list row shows its value and what it does
A native list row SHALL show its title, a summary of its value when it has one, and either that it opens something or the control that changes it. A row whose page is the one shown beside its list SHALL be marked as chosen, to the eye and to VoiceOver, and no other row of that list SHALL be.

#### Scenario: A row that opens a page
- **WHEN** a row for "Units" with the summary "Imperial" opens a page
- **THEN** it shows "Units", "Imperial" and the system's disclosure mark, and tapping anywhere on it opens the page

#### Scenario: The row of the page shown
- **WHEN** a list shows beside the page its "Privacy zones" row opened
- **THEN** that row is filled with the system's selection colour and VoiceOver reads it as selected, and the rows around it are not
