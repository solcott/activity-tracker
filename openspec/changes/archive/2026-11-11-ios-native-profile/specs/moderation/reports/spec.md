# Spec Delta

## MODIFIED Requirements

### Requirement: Report entry points
Clients SHALL offer Report on any activity, on each photo in the media gallery, on any comment, on any post, and on any athlete's profile, when someone other than the viewer wrote it. They SHALL NOT offer Report on the viewer's own content or profile, or to a signed-out viewer. The native profile on iOS SHALL NOT offer Report until the report sheet can be shown from a native page; it offers Mute and Block alone.

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
- **THEN** it offers Mute and Block, and not Report
