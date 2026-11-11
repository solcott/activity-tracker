# Spec Delta

## ADDED Requirements

### Requirement: Native posts and comments wait for the native report sheet
The native post card, the native post page's comments and the native quotes list on iOS SHALL NOT offer Report until the report sheet can be shown from a native page. Their other actions (Delete on the viewer's own quote; Delete, Hide or Unhide, Mute author and Block author on comments) SHALL be offered as in the shared UI.

#### Scenario: Someone else's post on iOS
- **WHEN** alice sees bob's post as a native card on iOS
- **THEN** its card offers no menu, and no Report

#### Scenario: Someone else's comment on iOS
- **WHEN** alice opens the menu on carol's comment on a native post page
- **THEN** it offers Mute author and Block author, and not Report
