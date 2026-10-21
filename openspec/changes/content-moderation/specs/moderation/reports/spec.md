# Spec Delta

## Purpose

Lets athletes report activities, photos, comments and accounts to the app's moderators, through the standard atproto reporting call sent to the app's own labeler.

## ADDED Requirements

### Requirement: Report entry points
Clients SHALL offer Report on any activity, on each photo in the media gallery, on any comment, and on any athlete's profile, when someone other than the viewer wrote it. They SHALL NOT offer Report on the viewer's own content or profile, or to a signed-out viewer.

#### Scenario: Report a comment
- **WHEN** alice opens the menu on bob's comment
- **THEN** Report is offered

#### Scenario: Own activity
- **WHEN** alice opens the menu on her own activity
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
A report on an activity or comment SHALL name it by `com.atproto.repo.strongRef`, using the URI and CID its view carries. A report on a photo SHALL name the activity, and the note SHALL say which photo by its position. A report on an account SHALL name its DID by `com.atproto.admin.defs#repoRef`.

#### Scenario: Photo report
- **WHEN** alice reports the third photo of bob's activity
- **THEN** the report's subject is the activity's URI and CID, and the note names photo 3

#### Scenario: Account report
- **WHEN** alice reports bob's profile
- **THEN** the report's subject is bob's DID

### Requirement: Reports go to the app's labeler through the PDS
A client SHALL send a report as `com.atproto.moderation.createReport` to the athlete's own PDS with the header `atproto-proxy: <labelerDid>#atproto_labeler`, where `labelerDid` is the app's configured labeler. It SHALL NOT send reports to any other labeler.

#### Scenario: Proxied report
- **WHEN** alice sends a report
- **THEN** her PDS receives `createReport` with the proxy header naming the app's labeler, and the labeler records a report from alice's DID

### Requirement: Report outcome
After a report is sent, the client SHALL thank the athlete and close the sheet. It SHALL offer to block the reported account when the subject has an author other than the viewer. On a failure it SHALL keep the reason and note and report the failure so the athlete can retry.

#### Scenario: Report sent
- **WHEN** alice's report of bob's comment succeeds
- **THEN** the sheet closes with a thank-you and offers to block bob

#### Scenario: Report fails
- **WHEN** sending the report fails
- **THEN** the sheet stays open with alice's reason and note, and says sending failed

### Requirement: Reporting non-public content
A report on a non-public activity or comment SHALL be sent in the same way as any other. Its subject URI and CID SHALL be the ones the AppView served the reporter, so moderators can look the subject up as `moderation/private-subjects` defines.

#### Scenario: Followers-only activity
- **WHEN** alice, an effective follower of bob, reports bob's Followers activity
- **THEN** the report reaches the labeler with the activity's URI and CID as the AppView served them to alice
