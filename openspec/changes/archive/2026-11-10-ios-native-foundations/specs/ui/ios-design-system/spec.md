## ADDED Requirements

### Requirement: Native screens share one set of controls
A native iOS screen SHALL draw its main, secondary and plain actions, its icon buttons, text fields, list rows, cards and progress with the app's native controls, so the same control looks and behaves the same on every native screen, in light and dark.

#### Scenario: The main action in dark
- **WHEN** a screen's main action is shown with the dark appearance
- **THEN** its label is readable against the app colour that fills it

#### Scenario: A disabled action
- **WHEN** a main action can't be taken
- **THEN** it is dimmed and does nothing when tapped

#### Scenario: An icon button
- **WHEN** VoiceOver reaches a button that shows only an icon
- **THEN** it reads the label the screen gave that button

### Requirement: A field says what is wrong with it
A native text field SHALL show its label, the text under it that explains it when there is some, and, when the screen marks it as wrong, that text in the error colour.

#### Scenario: A field in error
- **WHEN** a screen marks a field as wrong with the text "Enter a number"
- **THEN** the field shows "Enter a number" in the error colour

### Requirement: A list row shows its value and what it does
A native list row SHALL show its title, a summary of its value when it has one, and either that it opens something or the control that changes it.

#### Scenario: A row that opens a page
- **WHEN** a row for "Units" with the summary "Imperial" opens a page
- **THEN** it shows "Units", "Imperial" and the system's disclosure mark, and tapping anywhere on it opens the page

### Requirement: Errors read as on every platform
A native screen SHALL show an error as what failed and, when the reason is one the athlete can act on or wait out, why, in the error colour, never the failure's own text.

#### Scenario: A failure with Retry
- **WHEN** a native screen shows a failed load with a way to try again
- **THEN** it shows the error's text and a Retry button that runs the retry

### Requirement: Loaded content follows the loading rules natively
A native screen that shows loaded content SHALL follow `ui/content-loading`: progress before the first answer, the failure with Retry when nothing has loaded, and, once something has loaded, the content kept on screen with a failed refresh reported above it.

#### Scenario: Before the first answer
- **WHEN** a native screen's content hasn't answered yet
- **THEN** it shows progress and nothing else

#### Scenario: A first load that fails
- **WHEN** the first load fails because the server can't be reached
- **THEN** the screen shows what failed, "Couldn't reach the server." and Retry

#### Scenario: A refresh that fails
- **WHEN** content has loaded and a later refresh fails
- **THEN** the content stays and the failure shows above it with Retry

### Requirement: Icons and sport images take the text's size and colour
An app icon or a sport's image in a native view SHALL scale with the text beside it and take the colour the view gives it.

#### Scenario: Larger text
- **WHEN** the athlete uses a larger text size
- **THEN** a row's icon grows with its title
