# Spec Delta

## ADDED Requirements

### Requirement: Managing mounts is native on iOS
On iOS the owner's gear page SHALL offer Replace and Unmount for each component mounted now, in a menu on its row, and Mount a component under them, none of them usable while a mount change saves. Mount a component and Replace SHALL open a native sheet listing the athlete's items that can be mounted, titled as the shared sheet, with New item and Cancel; when there are none it SHALL say so. Unmount SHALL ask for the date in a native calendar whose first day is the day after the mount began, showing today by default (or that first day when it is later). A failed mount change SHALL show by Components, not over the activities.

#### Scenario: Replacing a chain
- **WHEN** the athlete chooses Replace on Chain #4 on the native page
- **THEN** a sheet titled "Replace Chain #4" lists Chain #5 and the power meter, and choosing Chain #5 replaces it from today

#### Scenario: Unmounting yesterday's mount
- **WHEN** the athlete chooses Unmount on a chain mounted today
- **THEN** the calendar offers no day before tomorrow and shows tomorrow

#### Scenario: Mount loop
- **WHEN** a mount change fails because it would make a loop
- **THEN** the error shows in the Components section of the native page
