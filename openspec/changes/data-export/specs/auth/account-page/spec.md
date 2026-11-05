# Spec Delta

## ADDED Requirements

### Requirement: The Account page offers a data export
The Account page SHALL have an Export your data row. With no export, it SHALL say what an export holds and start one. While one runs, it SHALL show its progress and offer Cancel. When one is ready, it SHALL offer Save and say what was left out, if anything.

#### Scenario: No export yet
- **WHEN** alice opens the Account page with no export
- **THEN** it offers Export your data, saying it downloads everything Activity Tracker holds of hers, including private activities and settings

#### Scenario: Export running
- **WHEN** alice opens the Account page while her export has read 120 of 400 activities
- **THEN** the row shows that progress and offers Cancel

#### Scenario: Export ready with problems
- **WHEN** alice's export finished with 1 item left out
- **THEN** the row offers Save and says 1 item couldn't be read

## MODIFIED Requirements

### Requirement: Signing out with pending work asks first
When the account has changes on this device that haven't uploaded (waiting, uploading or refused), an import still saving, or a data export running or waiting to be saved, Sign out SHALL first say how many changes haven't uploaded, that they stay on this device and upload when this account signs in here again, if an import is saving, that its unsaved activities will be dropped, and, if there's an export, that it will be cancelled and deleted. The athlete SHALL be able to cancel or sign out anyway; signing out anyway SHALL keep the queued writes.

#### Scenario: Uploads waiting
- **WHEN** alice has 3 activities waiting to upload and chooses Sign out
- **THEN** she is told 3 changes haven't uploaded and will upload when she signs in again on this device, and can cancel or sign out anyway

#### Scenario: Signing out anyway
- **WHEN** alice signs out anyway with 3 changes waiting, then later signs in as `alice.test` on the same device
- **THEN** the 3 changes upload after she signs in

#### Scenario: Cancelling
- **WHEN** alice cancels the sign-out question
- **THEN** she stays signed in on the Account page

#### Scenario: Import still saving
- **WHEN** bob chooses Sign out while a batch import is still saving
- **THEN** he is also told the import's unsaved activities will be dropped before he confirms

#### Scenario: Export not yet saved
- **WHEN** carol chooses Sign out while her data export is running
- **THEN** she is told the export will be cancelled and deleted, and signing out anyway stops it and deletes its files
