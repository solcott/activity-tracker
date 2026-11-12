# Spec Delta

## ADDED Requirements

### Requirement: The Moderation page is native on iOS
On iOS the Moderation page SHALL be drawn natively: the subject URI field with the page's help, Open, which is held while a subject
opens, a URI that isn't a record address or a failed open, and the subject: who may see it, then the activity as the activity card, or
the comment's author and text, or "not under review".

#### Scenario: A Followers comment
- **WHEN** a moderator opens the URI of bob's reported Followers comment on the iOS page
- **THEN** the page reads "Visible to: Followers" over bob's name and the comment's text

#### Scenario: Not an address
- **WHEN** a moderator types "hello" and taps Open
- **THEN** the page says that isn't a valid record address, and shows no subject
