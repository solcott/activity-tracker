## MODIFIED Requirements

### Requirement: A page in a column has a header of its own
A native page drawn in a column beside another screen, under that screen's navigation bar, SHALL show its title in a header centred at the top of its column, in the weight of a bar's title. Content SHALL scroll under it as under the system's bar, in light and dark, on every supported iOS version. A page that can go back from the column SHALL show a back button at the header's leading end, drawn and named as the system's is, with the title centred clear of it.

#### Scenario: A page beside a list
- **WHEN** a native page titled "Segments & groups" is drawn beside a list
- **THEN** "Segments & groups" is centred at the top of the page's column, below the bar over both columns

#### Scenario: Scrolled content
- **WHEN** the page in the column is scrolled
- **THEN** its rows pass under the header, which stays readable over them

#### Scenario: A deeper page
- **WHEN** a native page that can go back is drawn in the column
- **THEN** its header shows the system's back chevron at its leading end, VoiceOver reads it as Back, and tapping it goes back

#### Scenario: A page that can't go back right now
- **WHEN** a deeper page in the column says it can't go back, as a running leave does
- **THEN** its header shows no back button
