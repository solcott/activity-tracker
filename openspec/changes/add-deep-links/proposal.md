# Proposal

## Why

Nothing outside the app can open a page in it today. On every platform the only URL the app reads is its OAuth callback. A link someone shares (an activity, a segment, a trail, a profile) can't open the app, and on the web a reload or Back button always lands on the feed. Two notification taps (Record and Account) already open a screen from outside the UI through ad-hoc request channels. Deep linking turns that into one way of opening any page from a URL, on Android, iOS and the web.

## What Changes

- **A link grammar for every page you can link to.** Each `@CircuitSerializable` screen whose arguments can come from a URL gets one canonical path. Examples:
  - `/profile/{actor}`
  - `/profile/{actor}/activity/{rkey}`
  - `/segment/{did}/{rkey}`
  - `/trail/{did}/{rkey}`
  - `/dive-site/{did}/{rkey}`
  - `/settings/training-zones/history`

  Reading paths works for both DIDs and handles; written paths use DIDs. The login and leave screens and the browse lists embedded in Search are left out on purpose. A check fails the build for any new screen that has neither a path nor an explicit exclusion.
- **Three forms of the same link:**
  - https on the AppView's public host, as Android App Links and iOS Universal Links;
  - a fixed `activitytracker://` scheme, for development, testing and builds without a verified domain;
  - a bare path on the web app's own origin.

  `/oauth/*`, `/xrpc/*` and `/.well-known/*` are never links.
- **A link opens its page, and Back works from there.**
  - The page opens on the tab it belongs to: the viewer's own pages and Settings on You, everything else on the tab being shown.
  - Its parents go under it, so Back works as if the athlete had walked there. For example, a segment's leaderboard has the segment under it, and Training zones › History has Settings › Training zones under it.
  - A page already in the stack is reused rather than opened a second time.
  - An owner-only page (edit activity, add people, combine) linked by someone other than the owner opens the activity instead.
- **A link received while signed out waits, and opens after sign-in.** This includes the web's full-page OAuth redirect. The same applies while a leave is in progress.
- **Full browser history on the web.**
  - The address bar follows in-app navigation. Back and Forward move through the app's stack, and switching tabs is a history entry.
  - A reload rebuilds the stack the URL describes.
  - Signing out resets the URL to `/`.
- **The AppView serves `/.well-known/assetlinks.json` and `/.well-known/apple-app-site-association`** from configuration (Android package and signing certificate fingerprints, Apple app IDs). Each answers 404 when unconfigured.
- **Universal Links are optional on iOS.** Associated domains need a development team, so the default build is unchanged: the unsigned ad-hoc build keeps working, with the custom scheme.
- **Leaderboard, Mayors, Regulars and import batch screens no longer need their name passed in.** They load the name when opened from a link. In-app navigation still passes it.
- The Record and Account notification taps move onto the same link channel. Their behaviour is unchanged.

Out of scope: share and copy-link buttons in the UI (the grammar makes them a small follow-up), the desktop app, and a production web host.

## Capabilities

### New Capabilities
- `ui/deep-links`:
  - the link forms and host;
  - the path of each page and the exclusions;
  - the stack a link builds, including handles and the owner-only fallback;
  - links pending until sign-in;
  - unknown links ignored;
  - the well-known association files;
  - optional Universal Links.
- `ui/web-history`: the web app's address bar and Back/Forward following the app's stack, reloads, and sign-out.

### Modified Capabilities
- `ui/ios-native-shell`: "Requests from outside open their screen" also covers links, opened over the tab the link belongs to.
- `dev/presenter-host`: the host applies a link to its tabs' stacks (choosing the tab, reusing a screen already in the stack, and pushing the rest).
- `dev/module-boundaries`: the presenter host may also depend on the link module, which itself sees only contracts.

## Impact

- **New module `:app-links` (`app/links`):** the grammar, routes per feature, link plans, and round-trip and coverage tests. It is KMP with no Compose or Metro, and is not exported to Swift. It uses Ktor's `io.ktor.http` for URL parsing and encoding.
- **`:app-host`:**
  - New: `LinkRequests` (AppScope) and `LinkOpener` (SessionScope).
  - Changed: `SessionHost` consumes links.
  - Retired: `RecordScreenRequests` and `AccountScreenRequests`.
  - New accessors on `AppGraph` and `SessionGraph`.
- **`composeApp`:** `App.kt` consumes links, the tab switch is extracted from `AppShell`, and a back-stack sync hook is added.
- **Android:**
  - Manifest: an https `autoVerify` filter on the link host and an `activitytracker` scheme filter.
  - `MainActivity` handles links.
  - The `activitytracker.native.oauth` convention plugin and `GeneratedNetworkConfig` gain the link host.
- **iOS:**
  - `onOpenURL` and `onContinueUserActivity` in `iOSApp.swift`, plus `IosApp.openLink`.
  - A URL type in `Info.plist`.
  - An optional xcconfig and entitlements for associated domains, written by `scripts/dev.sh` when a team is configured.
- **Web:**
  - Absolute asset paths in `index.html` and Compose resources.
  - Start-up link handling and `sessionStorage` across the OAuth redirect.
  - Browser history sync.
- **AppView:** two well-known routes and new `AppViewConfig` settings (`ANDROID_APP_PACKAGE`, `ANDROID_CERT_SHA256`, `APPLE_APP_IDS`) passed through the local stack.
- **Features:** `SegmentLeaderboardScreen`, `SegmentMayorsScreen`, `DiveSiteRegularsScreen` and `ImportBatchScreen` take `name: String? = null`, with their presenters, fixtures and native views updated.
- **Docs:** `compose-screens.md`, `adaptive-ui.md`, the `new-screen` skill and CLAUDE.md say that a new screen adds a link route or an exclusion.
