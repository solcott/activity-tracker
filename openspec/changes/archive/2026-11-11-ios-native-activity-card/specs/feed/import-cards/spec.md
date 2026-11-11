# Spec Delta

## ADDED Requirements

### Requirement: The batch screen is native on iOS
On iOS the batch screen SHALL be drawn with the system's list and navigation bar, titled as the shared one, listing the batch's activities as native activity cards under a heading per month in the viewer's time zone, newest started first, page by page. A failed like SHALL show at the top, and its loading, empty and error states SHALL follow `ui/content-loading`.

#### Scenario: Two months
- **WHEN** the viewer opens Bob's batch holding a ride from November 2025 and a run from October 2025
- **THEN** the native page is titled "Imported by Bob" and lists "Nov 2025" over the ride's card and "Oct 2025" over the run's

#### Scenario: Opening an activity
- **WHEN** the viewer taps a card on the native batch page
- **THEN** that activity's page opens, as from the shared one

#### Scenario: Empty batch
- **WHEN** the batch holds no activity the viewer may see
- **THEN** the native page says so in the shared page's words
