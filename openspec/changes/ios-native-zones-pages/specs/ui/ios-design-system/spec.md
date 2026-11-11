# Spec Delta

## ADDED Requirements

### Requirement: A digits-only field keeps the digits of an edit
A native text field that takes digits only SHALL keep the digits of an edit and drop everything else, so a digit typed right after a refused character is kept, however fast it is typed or pasted. An edit that would make the text longer than the field's maximum length SHALL leave the text as it was.

#### Scenario: A letter between digits
- **WHEN** "9a5" is typed quickly into an empty digits-only field
- **THEN** the field reads "95"

#### Scenario: Pasted with a unit
- **WHEN** "170 bpm" is pasted into an empty digits-only field of at most 3 characters
- **THEN** the field reads "170"

#### Scenario: Too long
- **WHEN** "1700" is pasted into an empty digits-only field of at most 3 characters
- **THEN** the field stays empty

### Requirement: A blank form row shows its hint
A native form's text field row SHALL be able to show a hint at its trailing end while it is blank, in the placeholder's colour, such as the default a blank value falls back to, and VoiceOver SHALL read the hint with the label.

#### Scenario: A default behind a blank field
- **WHEN** a sport's max heart rate is blank and the default is 190
- **THEN** the row shows "Default: 190" where the value would be, and typing replaces it
