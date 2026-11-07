# Spec Delta

## MODIFIED Requirements

### Requirement: Label subjects
A label on an account DID SHALL apply to the account's profile, avatar, activities, comments, posts and reposts. A label on a record URI SHALL apply to that record, at any CID unless the label names one. A label on an activity SHALL apply to all of its photos, and a label on a profile record to its avatar. Labels SHALL NOT target a single photo.

#### Scenario: Account label covers content
- **WHEN** bob's DID carries `!hide`
- **THEN** bob's profile, activities, comments, posts and reposts are all hidden

#### Scenario: Activity label covers its photos
- **WHEN** the labeler labels bob's activity `graphic-media`
- **THEN** every photo of that activity is blurred

#### Scenario: Label pinned to a version
- **WHEN** a label on bob's activity names a CID, and bob then edits the activity so its CID changes
- **THEN** the label no longer applies to the edited activity

#### Scenario: Post label
- **WHEN** the labeler labels bob's post `!takedown`
- **THEN** the post is served to no one, and the proposal it shares is unaffected

#### Scenario: Shared proposal taken down
- **WHEN** a proposal bob shared is labelled `!takedown`
- **THEN** his post sharing it is no longer served either

#### Scenario: Reposted post taken down
- **WHEN** the labeler labels alice's post `!takedown` after bob reposted it
- **THEN** the post leaves the timelines of bob's followers, and bob's repost isn't counted or listed
