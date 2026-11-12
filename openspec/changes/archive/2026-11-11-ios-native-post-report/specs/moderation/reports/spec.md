# Spec Delta

## MODIFIED Requirements

### Requirement: Report entry points
Clients SHALL offer Report on any activity, on each photo in the media gallery, on any comment, on any post, and on any athlete's profile, when someone other than the viewer wrote it. They SHALL NOT offer Report on the viewer's own content or profile, or to a signed-out viewer. The native profile on iOS SHALL offer Report in its menu beside Mute and Block, and show the report sheet natively. The native post card (on the post page, in the quotes list and on the profile's Posts tab), the native post page's comments and the native quotes list on iOS SHALL offer Report where the shared UI does, and show the report sheet natively.

#### Scenario: Report a comment
- **WHEN** alice opens the menu on bob's comment
- **THEN** Report is offered

#### Scenario: Report a post
- **WHEN** alice opens the menu on bob's post in her timeline
- **THEN** Report is offered

#### Scenario: Own activity
- **WHEN** alice opens the menu on her own activity
- **THEN** Report is not offered

#### Scenario: Account the viewer blocks
- **WHEN** alice opens the profile of bob, whom alice blocks
- **THEN** Report is offered next to Unblock

#### Scenario: A profile on iOS
- **WHEN** alice opens the menu on bob's profile on iOS
- **THEN** it offers Mute, Block and Report, and Report opens the native report sheet for bob's account

#### Scenario: Own profile on iOS
- **WHEN** alice opens her own profile on iOS
- **THEN** Report is not offered

#### Scenario: Someone else's post on iOS
- **WHEN** alice opens the menu on bob's post as a native card on iOS, with a labeler configured
- **THEN** it offers Report, which opens the native report sheet for bob's post

#### Scenario: Someone else's comment on iOS
- **WHEN** alice opens the menu on carol's comment on a native post page, with a labeler configured
- **THEN** it offers Mute author, Block author and Report, and Report opens the native report sheet for carol's comment

#### Scenario: Own quote on iOS
- **WHEN** alice opens the menu on her own quote in the native quotes list
- **THEN** it offers Delete quote, and not Report

## REMOVED Requirements

### Requirement: Native posts and comments wait for the native report sheet
**Reason**: The native report sheet can be shown from a native page (`ios-native-report-sheet`), and the native posts offer Report now.
**Migration**: Covered by "Report entry points", which says where the native posts offer Report.
