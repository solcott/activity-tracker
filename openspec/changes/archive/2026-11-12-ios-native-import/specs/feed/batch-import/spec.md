# Spec Delta

## ADDED Requirements

### Requirement: Importing is native on iOS
On iOS the import screen SHALL be drawn natively with the same content as the shared screen: one activity's preview and form with Save in the bar; several activities' summary, filters, rows by month and batch settings with Save; an opened row in a sheet; and the saved and after-import panels.

#### Scenario: Opening a row
- **WHEN** alice taps a batch row
- **THEN** a sheet shows its type, title and page as it would publish, and Done or a swipe down closes it

### Requirement: Picking files natively on iOS
On iOS the import screen SHALL pick files with the system's document picker, several at once and of any type, and pass them on as sources read a range at a time, never read whole. It SHALL show reading's progress with Cancel, a batch save under way, and each stopped import with Dismiss.

#### Scenario: Picking several files
- **WHEN** alice taps Choose files and picks two GPX files
- **THEN** the screen shows that it is reading, then the batch's two activities by month with both selected and Save 2 activities

#### Scenario: Cancelling a read
- **WHEN** alice taps Cancel while the picked files are read
- **THEN** reading stops and the screen shows Choose files again

#### Scenario: A stopped import
- **WHEN** alice opens Import after an import's save stopped at 120 of 400 and taps Dismiss on its notice
- **THEN** the notice goes away
