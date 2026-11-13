## ADDED Requirements

### Requirement: Joined text is put together once
Text a UI shows as one piece made of several (sentences in a note, paragraphs in a dialog's message) SHALL be joined by shared code,
through shared strings, so both UIs show the same piece. A list shown as bullets SHALL have its lines chosen by shared code and its mark
be one shared string, which both UIs draw before each line, with the line wrapping under itself.

#### Scenario: A form's public note
- **WHEN** the create-segment form names the activity it is cut from, or the create-dive-site form names its dive
- **THEN** both UIs show the note's sentences as one paragraph, a space between them

#### Scenario: The end dialog
- **WHEN** an athlete signs out with two changes not uploaded and an import saving
- **THEN** both UIs' dialog says each in its own paragraph, a blank line between them

#### Scenario: Leave's bullets
- **WHEN** the Leave page lists what is deleted and what stays
- **THEN** both UIs show the same lines in the same order, each after the same mark

### Requirement: A profile names its country the way Edit profile does
The profile's location line SHALL name the athlete's country with the same lookup Edit profile's country choice uses, in the device's
language, falling back to the code.

#### Scenario: A country
- **WHEN** an athlete's profile location has the country `FR` on an English device
- **THEN** its location line ends "France" in both UIs
