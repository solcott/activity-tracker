# ui/ios-design-system Specification

## Purpose
What the native iOS app's own controls and its loaded-content layout do, so every native screen looks and behaves alike and follows the app's rules for errors and loading.

## Requirements

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
A native list row SHALL show its title, a summary of its value when it has one, and either that it opens something or the control that changes it. A row whose page is the one shown beside its list SHALL be marked as chosen, to the eye and to VoiceOver, and no other row of that list SHALL be.

#### Scenario: A row that opens a page
- **WHEN** a row for "Units" with the summary "Imperial" opens a page
- **THEN** it shows "Units", "Imperial" and the system's disclosure mark, and tapping anywhere on it opens the page

#### Scenario: The row of the page shown
- **WHEN** a list shows beside the page its "Privacy zones" row opened
- **THEN** that row is filled with the system's selection colour and VoiceOver reads it as selected, and the rows around it are not

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

### Requirement: Pictures from the network show with a placeholder
A native screen SHALL show a photo or an avatar from its URL filling its frame, with a placeholder in its place until it loads, when it fails, and when there is none. A photo this device uploaded and holds for the session (a `heldphoto:` URL) SHALL show from those bytes, as on the other platforms. An avatar SHALL show the athlete's initial until its picture loads.

#### Scenario: An item with no photo
- **WHEN** a gear item has no photo
- **THEN** its row shows the placeholder in the photo's place

#### Scenario: A non-public photo
- **WHEN** alice's gear item visible only to her has a photo the session holds
- **THEN** its row shows that photo

#### Scenario: An athlete without a picture
- **WHEN** an account named Bob has no avatar
- **THEN** its avatar shows "B"

### Requirement: A paged list follows the loading rules natively
A native screen that shows a paged list SHALL follow `ui/content-loading` as the shared UI's paged list does: progress or the first page's failure with Retry until the first answer; once answered, a refresh's progress or failure above the items, the empty message when the list holds nothing, and the next page's progress or failure with Retry below the items. It SHALL ask for the next page once the end of the items is on screen and more can be loaded, and not before.

#### Scenario: A failed next page
- **WHEN** a list's next page fails because the server can't be reached
- **THEN** the loaded items stay, and below them it reads that it couldn't reach the server, with Retry

#### Scenario: The end not yet on screen
- **WHEN** a list has loaded more items than fit on screen, and more remain
- **THEN** no further page is asked for until the last loaded item is scrolled onto the screen

### Requirement: A page's actions sit beside its title
A native page's actions (such as Add) SHALL show at the trailing end of the stack's bar when the page is alone, and at the trailing end of its column header when it is drawn beside another screen.

#### Scenario: Gear beside the list
- **WHEN** Gear is drawn beside Settings' list
- **THEN** its header reads "Gear" with Add and More at its trailing end

### Requirement: Native form text fields sit in the form's rows
A native form's text field SHALL be able to show as a row of the system's grouped form, its label leading and its text trailing on one line, or as its label above several lines of text. The text that explains it, or its error, SHALL show under the row, and VoiceOver SHALL read the label with the text.

#### Scenario: A field marked wrong
- **WHEN** a form marks its name field as wrong with "Give your gear a name"
- **THEN** that text shows under the row in the error colour

### Requirement: Native form menu choices keep their choice trailing
A native form's menu choice SHALL show its label leading, wrapping when long, and the choice with the system's menu mark at the trailing end on one line, and SHALL open the choices with the current one checked. VoiceOver SHALL read its name with the choice.

#### Scenario: A long label beside a choice
- **WHEN** a choice's label is too long for its row's width
- **THEN** the label wraps and the choice stays on one line at the trailing edge
