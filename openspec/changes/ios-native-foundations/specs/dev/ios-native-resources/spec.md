## ADDED Requirements

### Requirement: Every shared string is in the iOS app under its name
The iOS app SHALL hold every string, plural and string array item of the shared UI's strings, under the name the shared UI gives it and with the same English text, generated from the shared strings rather than typed. A shared placeholder SHALL take the same argument in the same position on iOS.

#### Scenario: A plain string
- **WHEN** a native view shows the string named `login_intro`
- **THEN** it reads as the shared UI's `login_intro` does

#### Scenario: A string with arguments
- **WHEN** a native view shows `error_with_reason` with "Couldn't load your feed" and "Couldn't reach the server."
- **THEN** it reads "Couldn't load your feed. Couldn't reach the server."

#### Scenario: A percent sign
- **WHEN** a native view shows a battery level of 80 with the string the shared UI writes as `Battery %1$s%`
- **THEN** it reads "Battery 80%"

#### Scenario: An item of a string array
- **WHEN** a native view shows the third segment rank ordinal
- **THEN** it reads "3rd"

### Requirement: Plurals choose their form by quantity
A plural SHALL read in the form its quantity selects, whatever its arguments are, as it does in the shared UI.

#### Scenario: One and many
- **WHEN** a native view shows `like_count` for 1 like and for 3 likes
- **THEN** they read "1 like" and "3 likes"

#### Scenario: A quantity that isn't the first argument
- **WHEN** a native view shows `card_with_others` for the athlete "bob" and 1 other
- **THEN** it reads "with bob and 1 other"

### Requirement: App icons are the same symbols in Swift
The iOS app SHALL have, for every icon the shared design system names, the SF Symbol the shared UI draws for it on iOS, outlined and filled, generated from the shared definition.

#### Scenario: An icon with a filled form
- **WHEN** a native view draws the Feed tab's icon selected and unselected
- **THEN** it draws the same two symbols the shared UI's tab drew

#### Scenario: A new icon
- **WHEN** an icon is added to the shared design system and the iOS resources are generated
- **THEN** Swift can name it without a line written by hand

### Requirement: Sport images are the shared drawables
The iOS app SHALL hold each vendored drawable of the shared UI as an image a native view can draw at any size and tint, generated from the same file.

#### Scenario: A sport's icon
- **WHEN** a native view draws the icon of a run at 24 points in the accent colour
- **THEN** it shows the same shape the shared UI shows for a run, sharp, in that colour

### Requirement: Generated iOS resources match their sources
`./gradlew check` SHALL fail, naming the task that regenerates them, when the iOS app's generated strings, icons or images differ from what the shared sources now produce. It SHALL run on any machine, without Xcode.

#### Scenario: A shared string is reworded
- **WHEN** `login_intro` is changed in the shared strings and the iOS resources aren't regenerated
- **THEN** `./gradlew check` fails and says to run `./gradlew generateIosResources`

#### Scenario: A generated file is edited by hand
- **WHEN** an entry of the iOS String Catalog is edited and the shared string isn't
- **THEN** `./gradlew check` fails

#### Scenario: Regenerated
- **WHEN** `./gradlew generateIosResources` has run since the shared sources last changed
- **THEN** `./gradlew check` passes this check, on Linux as on macOS

### Requirement: A string that no longer exists stops the iOS build
A native view that names a shared string SHALL fail to compile once that string is removed or renamed and the resources are regenerated.

#### Scenario: A removed string
- **WHEN** `login_intro` is removed from the shared strings, the iOS resources are regenerated and the sign-in view still shows it
- **THEN** the iOS build fails at that line
