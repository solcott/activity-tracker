# Spec Delta

## MODIFIED Requirements

### Requirement: Settings is a grouped list
Settings SHALL be a list of titled sections, in order: Account (one row, the signed-in handle, opening the Account page), General (units), Privacy & sharing (Activity defaults, Privacy zones, Segments & groups), Training (Training zones, Gear), Safety (Blocked accounts, Muted accounts), then Moderation where offered. Sign out SHALL NOT be on the list; it is on the Account page. Each row that opens a page SHALL be drawn as a navigation row, not a button.

#### Scenario: Sections in order
- **WHEN** an athlete who isn't a moderator opens Settings
- **THEN** it shows Account, General, Privacy & sharing, Training and Safety in that order, with no Moderation row and no Sign out row

#### Scenario: Moderator on desktop
- **WHEN** a moderator opens Settings on desktop or web
- **THEN** a Moderation row shows after Safety, last in the list

#### Scenario: Account row
- **WHEN** alice, signed in as `alice.test`, opens Settings
- **THEN** the first section is Account, with one row reading `alice.test` that opens the Account page

## ADDED Requirements

### Requirement: Account row flags a sign-in that needs renewing
The Account row SHALL show a short note when the Account page would offer Sign in again, and none otherwise.

#### Scenario: Out-of-date sign-in
- **WHEN** bob's session has no athlete-space scope and he opens Settings
- **THEN** the Account row shows that he needs to sign in again

#### Scenario: Current sign-in
- **WHEN** alice's sign-in is up to date
- **THEN** the Account row shows only her handle

### Requirement: The Account page opens in the detail pane
The Account page SHALL be a Settings page: on a wide window it opens in the detail pane with the Account row marked, and on a narrow one it replaces Settings with back returning to the list.

#### Scenario: Account on desktop
- **WHEN** on a wide desktop window an athlete chooses the Account row
- **THEN** the Account page shows in the detail pane without a back button, and the Account row is marked
