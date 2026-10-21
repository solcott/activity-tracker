# Proposal

## Why

Athletes publish photos, titles, descriptions, comments and profiles, but there is no way to report abuse and no way to remove or hide content. Blocking, which has landed (`graph/blocks`, `graph/mutes`), protects one athlete from another. Content moderation protects everyone and gives the operators a way to act. atproto already defines the pieces: reports (`com.atproto.moderation.createReport`), labels (`com.atproto.label.*`) and a self-hostable labeler with a moderator UI (Ozone). Bluesky's labeler already flags problem accounts. We reuse all of this rather than inventing our own.

## What Changes

- **Our labeler.** A self-hosted Ozone service, with its own DID declaring `#atproto_labeler`, is the app's report queue and label source. It joins the local docker stack and the AppView configuration.
- **Reports.**
  - Clients can report an activity, a photo on it, a comment, or an account, with a reason type and optional text.
  - Report sits beside Block and Mute in the comment and profile menus, and in new menus on others' activities and on gallery photos. After a report the app offers to block the author, using the existing block flow.
  - The report goes through the athlete's PDS to our labeler, using the `atproto-proxy` header.
  - No client SDK is used: it's one hand-written `XrpcProcedure`.
- **Labels the AppView honors.**
  - The AppView subscribes to our labeler's label stream, and to Bluesky's moderation labeler for account-level labels only. It verifies label signatures and handles negation and expiry.
  - It stores the labels and attaches the ones that apply to activity, comment and profile views, and to each photo.
- **What labels do in v1. These behaviors are fixed; viewer preferences come later.**
  - `!takedown` removes the subject everywhere, the way a block does for one pair of athletes: a taken-down profile answers not found, and the AppView refuses its media to everyone. An account taken down also loses access to its tier spaces for other readers.
  - `!hide` hides the subject from lists and feeds but leaves it reachable by URI behind a warning.
  - `porn`, `sexual`, `nudity` and `graphic-media` blur media, with tap-to-reveal.
  - `!warn`, `spam` and `impersonation` show a warning.
- **Account status.** The AppView stops serving an account whose Jetstream `#account` status is `takendown`, `suspended` or `deactivated`, and serves it again when it is active. Today only `deleted` is handled.
- **Moderators and non-public content.**
  - Ozone can't read tier spaces. The AppView joins the Ozone team with a read-only role.
  - It serves a moderator-only view of a reported non-public subject, only while Ozone has an open report or review on it. Every view is audit-logged.
  - Moderators open such a subject from a Moderation entry in the desktop and web Settings.
- Not covered: automated classification of images and text (it is in BACKLOG.md), video (no video media exists yet), viewer label preferences and subscribing to third-party labelers, and appeals UI in the app.

## Capabilities

### New Capabilities
- `moderation/reports`: How athletes report activities, photos, comments and accounts, and how a report reaches the labeler.
- `moderation/labels`: Which labelers the AppView trusts, how it ingests and verifies labels, and what each label value does to what is served and shown.
- `moderation/account-status`: How hosting status from the network (`takendown`, `suspended`, `deactivated`) changes what the AppView serves.
- `moderation/private-subjects`: How moderators see reported non-public content through the AppView, and the limits on that access.

### Modified Capabilities
- `dev/local-stack`: The local stack runs a labeler (Ozone) with a local labeler account, wired to the PDS and the AppView.

## Impact

- **docker:** `docker/compose.yaml` gains an `ozone` service, its Postgres database and setup of the labeler account's DID document (`#atproto_labeler` service and `#atproto_label` key) on the local PLC, plus a `scripts/dev.sh` step.
- **Lexicons and the Kotlin mirror:**
  - `com.atproto.moderation.createReport` and `com.atproto.label.defs#label` models in `core/lexicon`.
  - `labels` on `feed.defs#activityView`, `#commentView` and `#imageView`, and on `actor.defs#profileView`.
  - A new `app.activitytracker.moderation.getSubject` query for moderators.
- **AppView:**
  - Labeler config in `AppViewConfig`.
  - A label subscriber (`com.atproto.label.subscribeLabels` with a resume cursor) and a server-side CBOR dependency.
  - Labels, label cursors, the moderator audit log and `actors.status`, in Flyway `V28` (after user-blocking's `V26` and the segments work's `V27`).
  - `served` and `unhidden` predicates in a new `api/Moderation.kt`, next to `api/Blocks.kt` and used the same way: `served` in both branches of `visibleTo` and in `SpaceAccess`, and `unhidden` next to each `notMuted` / `notBlocked` list filter. Also `ActorDetails.kt` (profile not found) and `BlobRedirects.kt`.
  - `#account` status handling in `Indexer`.
  - The moderator route, with an Ozone status check and an audit log.
- **Client:**
  - `XrpcClient` proxy-header support.
  - A report sheet reached from the comment menu and the profile's block and mute menu, and from new menus on others' activities and on gallery photos. Its block offer uses `BlockRepository`.
  - Blur, warning and hidden treatments in `composeApp` through the `ui/adaptive` wrappers.
- **Operations:** Ozone is a new service to run, with its own database and moderator accounts. The privacy policy must say that moderators can view reported non-public content.
