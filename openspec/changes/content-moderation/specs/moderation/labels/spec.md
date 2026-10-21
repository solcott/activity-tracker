# Spec Delta

## Purpose

Defines which labelers the AppView trusts, how it takes in and verifies their labels, and what each label value does to the activities, photos, comments and profiles the AppView serves and the clients show.

## ADDED Requirements

### Requirement: Trusted labelers
The AppView SHALL honor labels from exactly two sources, set in its configuration: the app's own labeler, and Bluesky's moderation labeler. From the app's labeler it SHALL honor labels on any subject. From Bluesky's labeler it SHALL honor only labels whose subject is an account DID. Labels from any other source SHALL be ignored.

#### Scenario: Bluesky label on a record ignored
- **WHEN** Bluesky's labeler labels a record URI
- **THEN** the AppView stores nothing from it

#### Scenario: Bluesky account label honored
- **WHEN** Bluesky's labeler labels bob's DID `spam`
- **THEN** the AppView applies `spam` to bob's account

### Requirement: Labels arrive by subscription
The AppView SHALL subscribe to each trusted labeler's `com.atproto.label.subscribeLabels` stream. It SHALL store its cursor and resume from it after a restart or disconnect, so no label is lost or applied twice. A failure on one labeler's stream SHALL NOT stop the other stream or indexing.

#### Scenario: Restart resumes
- **WHEN** the AppView restarts while the labeler emits labels
- **THEN** it resumes from its stored cursor and applies every label emitted meanwhile once

#### Scenario: One stream down
- **WHEN** Bluesky's labeler is unreachable
- **THEN** labels from the app's labeler still apply and Jetstream indexing continues

### Requirement: Labels are verified
The AppView SHALL apply a label only when its `src` is the labeler whose stream it came from and its signature verifies against that labeler's `#atproto_label` key in its DID document. A label that fails SHALL be dropped and logged.

#### Scenario: Bad signature
- **WHEN** a label arrives whose signature doesn't verify against the labeler's current key
- **THEN** the AppView drops it

### Requirement: Negation and expiry
A label with `neg: true` SHALL remove the matching label (same source, subject and value). A label with an `exp` in the past SHALL no longer apply. Only the newest label for a source, subject and value SHALL count.

#### Scenario: Takedown reversed
- **WHEN** the labeler emits `!takedown` on bob's activity and later the same label with `neg: true`
- **THEN** bob's activity is served again as before

#### Scenario: Expired warning
- **WHEN** a `!warn` label on bob's account has an `exp` that has passed
- **THEN** bob's account shows no warning

### Requirement: Label subjects
A label on an account DID SHALL apply to the account's profile, avatar, activities and comments. A label on a record URI SHALL apply to that record, at any CID unless the label names one. A label on an activity SHALL apply to all of its photos, and a label on a profile record to its avatar. Labels SHALL NOT target a single photo.

#### Scenario: Account label covers content
- **WHEN** bob's DID carries `!hide`
- **THEN** bob's profile, activities and comments are all hidden

#### Scenario: Activity label covers its photos
- **WHEN** the labeler labels bob's activity `graphic-media`
- **THEN** every photo of that activity is blurred

#### Scenario: Label pinned to a version
- **WHEN** a label on bob's activity names a CID, and bob then edits the activity so its CID changes
- **THEN** the label no longer applies to the edited activity

### Requirement: Takedown removes content
A subject with `!takedown` SHALL NOT be served to anyone but its author. For other viewers it SHALL be left out of every feed, list, count and search, and a direct lookup SHALL answer as not found. Its media SHALL NOT be served or redirected to anyone but its author. Its author SHALL see it marked as taken down.

#### Scenario: Taken-down activity
- **WHEN** bob's activity carries `!takedown`
- **THEN** it's gone from every timeline and count, a lookup by URI answers not found for others, and bob sees it marked as taken down

#### Scenario: Taken-down activity's photos
- **WHEN** bob's activity carries `!takedown`
- **THEN** the AppView refuses to serve or redirect its photos to anyone but bob

#### Scenario: Taken-down account
- **WHEN** bob's DID carries `!takedown`
- **THEN** his profile answers not found for others, and his activities and comments are not served to others

### Requirement: Hide keeps content reachable behind a warning
A subject with `!hide` SHALL be left out of feeds, lists, counts and search for viewers other than its author. A direct lookup SHALL still return it carrying the label. Clients SHALL show it collapsed behind a warning the viewer can open.

#### Scenario: Hidden comment
- **WHEN** bob's comment carries `!hide`
- **THEN** it isn't listed or counted for others, and bob still sees it

#### Scenario: Hidden activity by URI
- **WHEN** alice opens a link to bob's activity that carries `!hide`
- **THEN** she sees a warning first and can choose to view it

### Requirement: Media labels blur
Clients SHALL blur any photo or avatar whose activity, profile or account is labeled `porn`, `sexual`, `nudity` or `graphic-media`. They SHALL show the reason and reveal it on tap. Revealing SHALL last only for that view of that photo. The athlete SHALL see their own labeled media blurred too, with a note saying why.

#### Scenario: Blurred photo
- **WHEN** an activity carries `nudity`
- **THEN** feed rows and the gallery show it blurred with "Nudity" until the viewer taps to reveal

#### Scenario: Reveal doesn't stick
- **WHEN** alice reveals a blurred photo and leaves the screen
- **THEN** the photo is blurred again next time

### Requirement: Warning labels
Clients SHALL show a warning, naming the label, on content or an account labeled `!warn`, `spam` or `impersonation`, without hiding it.

#### Scenario: Spam account
- **WHEN** bob's DID carries `spam`
- **THEN** bob's profile and comments show a "Spam" warning but stay visible

### Requirement: Labels in views
The AppView SHALL include the labels that apply to each activity, comment and profile it serves, from both trusted labelers, with their source and value. It SHALL leave out negated and expired labels, and labels from untrusted sources.

#### Scenario: Activity view carries labels
- **WHEN** a viewer fetches an activity that carries `graphic-media` and whose author carries `spam`
- **THEN** the view carries `graphic-media` on the activity and `spam` on the author

### Requirement: Unknown label values are ignored
Clients and the AppView SHALL treat label values they don't know as having no effect. They SHALL NOT fail on them.

#### Scenario: New label value
- **WHEN** the labeler emits `rude` on a comment
- **THEN** the comment is shown normally and nothing fails
