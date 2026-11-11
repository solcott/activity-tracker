# moderation/reports Specification

## Purpose

Lets athletes report activities, photos, comments and accounts to the app's moderators, through the standard atproto reporting call sent to the app's own labeler.

## Requirements

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

### Requirement: Report reasons
The report sheet SHALL ask for a reason: spam, sexual content, violence or graphic content, harassment or rude behavior, misleading or impersonation, or other. It SHALL offer an optional note of at most 2000 characters. Each reason SHALL map to its `com.atproto.moderation.defs#reasonType`, and "other" SHALL require the note.

#### Scenario: Harassment report
- **WHEN** alice reports bob's comment as harassment with no note
- **THEN** the report is sent with reason type `com.atproto.moderation.defs#reasonRude`

#### Scenario: Other without a note
- **WHEN** alice picks "other" and leaves the note empty
- **THEN** the sheet doesn't let her send the report

### Requirement: Report subject
A report on an activity, comment or post SHALL name it by `com.atproto.repo.strongRef`, using the URI and CID its view carries. A report on a photo SHALL name the activity, and the note SHALL say which photo by its position. A report on an account SHALL name its DID by `com.atproto.admin.defs#repoRef`.

#### Scenario: Photo report
- **WHEN** alice reports the third photo of bob's activity
- **THEN** the report's subject is the activity's URI and CID, and the note names photo 3

#### Scenario: Post report
- **WHEN** alice reports bob's post
- **THEN** the report's subject is the post's URI and CID, not the proposal it shares

#### Scenario: Account report
- **WHEN** alice reports bob's profile
- **THEN** the report's subject is bob's DID

### Requirement: Reports go to the app's labeler through the PDS
A client SHALL send a report as `com.atproto.moderation.createReport` to the athlete's own PDS with the header `atproto-proxy: <labelerDid>#atproto_labeler`, where `labelerDid` is the app's configured labeler. It SHALL NOT send reports to any other labeler.

#### Scenario: Proxied report
- **WHEN** alice sends a report
- **THEN** her PDS receives `createReport` with the proxy header naming the app's labeler, and the labeler records a report from alice's DID

### Requirement: Report outcome
After a report is sent, the client SHALL thank the athlete and close the sheet. It SHALL offer to block the reported account when the subject has an author other than the viewer and the viewer doesn't already block them. Accepting SHALL go through the same confirmation and block as the profile's Block action. On a failure it SHALL keep the reason and note and report the failure so the athlete can retry.

#### Scenario: Report sent
- **WHEN** alice's report of bob's comment succeeds
- **THEN** the sheet closes with a thank-you and offers to block bob

#### Scenario: Already blocking
- **WHEN** alice reports bob's profile while alice already blocks bob
- **THEN** the sheet closes with a thank-you and no offer to block

#### Scenario: Report fails
- **WHEN** sending the report fails
- **THEN** the sheet stays open with alice's reason and note, and says sending failed

### Requirement: Reporting non-public content
A report on a non-public activity or comment SHALL be sent in the same way as any other. Its subject URI and CID SHALL be the ones the AppView served the reporter, so moderators can look the subject up as `moderation/private-subjects` defines.

#### Scenario: Followers-only activity
- **WHEN** alice, an effective follower of bob, reports bob's Followers activity
- **THEN** the report reaches the labeler with the activity's URI and CID as the AppView served them to alice

### Requirement: Native posts and comments wait for the native report sheet
The native post card, the native post page's comments and the native quotes list on iOS SHALL NOT offer Report until the report sheet can be shown from a native page. Their other actions (Delete on the viewer's own quote; Delete, Hide or Unhide, Mute author and Block author on comments) SHALL be offered as in the shared UI.

#### Scenario: Someone else's post on iOS
- **WHEN** alice sees bob's post as a native card on iOS
- **THEN** its card offers no menu, and no Report

#### Scenario: Someone else's comment on iOS
- **WHEN** alice opens the menu on carol's comment on a native post page
- **THEN** it offers Mute author and Block author, and not Report
