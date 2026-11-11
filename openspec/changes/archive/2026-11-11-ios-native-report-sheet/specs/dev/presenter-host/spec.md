# Spec Delta

## ADDED Requirements

### Requirement: A sheet's sub-screen lasts while the sheet shows
A native UI that shows a sub-screen in a sheet SHALL close its sub-screen in the host when the sheet is dismissed, however it was dismissed, so the next sheet of an equal screen starts afresh. While the sheet slides away it SHALL go on drawing the sub-screen's last state.

#### Scenario: Report sent, then reopened
- **WHEN** alice sends a report of bob's profile, taps Done, and opens Report on bob's profile again
- **THEN** the sheet asks for a reason again, with no reason chosen and an empty note

#### Scenario: Swiped away
- **WHEN** alice picks a reason, swipes the report sheet down and opens Report again
- **THEN** the host has dropped the first sheet's state, and the profile heard that the sheet closed

### Requirement: The report sheet runs through the host
The host SHALL run the report sheet as the sub-screen of the screen carrying it, with the real report presenter: choosing a reason, sending once, the thanks, and the Block offer reaching the carrier, which then asks its Block confirmation.

#### Scenario: Report and block through the host
- **WHEN** a UI opens the report sheet from a carrier's `Reporting`, picks harassment, sends, and takes the Block offer
- **THEN** one report is sent, the sheet's state says it was sent, the carrier's sheet closes and its Block confirmation names the author
