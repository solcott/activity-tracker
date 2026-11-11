# Spec Delta

## MODIFIED Requirements

### Requirement: Report entry points
Clients SHALL offer Report on any activity, on each photo in the media gallery, on any comment, on any post, and on any athlete's profile, when someone other than the viewer wrote it. They SHALL NOT offer Report on the viewer's own content or profile, or to a signed-out viewer. The native profile on iOS SHALL offer Report in its menu beside Mute and Block, and show the report sheet natively.

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
