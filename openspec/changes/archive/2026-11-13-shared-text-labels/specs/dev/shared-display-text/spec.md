## ADDED Requirements

### Requirement: Feature labels are chosen once
A feature's labels that follow a rule (a metric card's title, an athlete named by their display name else their `@handle`, a toggle's
words for its state, a gear section's header, Save while a save runs, a tab's title, the activity visibility note, a comment's
confirmation, a picker's choice with its null option, the data export's steps and the account row's summary) SHALL be chosen by a shared
function that both UIs ask. Where the UIs differed, the shared wording SHALL be the one both read.

#### Scenario: Save while saving
- **WHEN** an athlete taps Save on Edit activity, Edit profile, Default zones, Sport zones or Manual entry and the save is running
- **THEN** the button reads "Saving…" in both UIs, and "Save" otherwise

#### Scenario: A name with no display name
- **WHEN** the author of a comment with no display name, whose handle is `carol.test`, is named in its report
- **THEN** both UIs name them "@carol.test", and an author with a display name by that name

#### Scenario: A comment's confirmation
- **WHEN** the viewer chooses Delete on their comment, or Block author on Carol's
- **THEN** both UIs ask with the delete title, message and Delete, or with "Block Carol?", its message and Block; no other action asks

#### Scenario: A picker's null choice
- **WHEN** segment browse's sport chip, a new device's kind, mount and profile link, and the zones models are listed
- **THEN** the null option reads "All", "None", "Nothing" and "Don't link" in both UIs, and each other option its name; a zones model reads its name

#### Scenario: The export's steps
- **WHEN** the Account page's export is ready
- **THEN** both UIs offer Save as the primary button and "Delete export" beside it, and while it runs only Cancel
