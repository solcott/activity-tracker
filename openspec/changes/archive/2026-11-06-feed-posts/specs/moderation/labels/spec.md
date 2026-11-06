# Spec Delta

## MODIFIED Requirements

### Requirement: Label subjects
A label on an account DID SHALL apply to the account's profile, avatar, activities, comments and posts. A label on a record URI SHALL apply to that record, at any CID unless the label names one. A label on an activity SHALL apply to all of its photos, and a label on a profile record to its avatar. Labels SHALL NOT target a single photo.

#### Scenario: Account label covers content
- **WHEN** bob's DID carries `!hide`
- **THEN** bob's profile, activities, comments and posts are all hidden

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
