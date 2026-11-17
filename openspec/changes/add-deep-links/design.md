# Design

## Context

See proposal.md, "Why", for the motivation. The current state that shapes this design:

- **Incoming URLs today are only the OAuth callback.**
  - Android: `MainActivity` is `singleTask`. `onCreate` and `onNewIntent` call `handleOAuthRedirect`, which checks `path == /oauth/callback`, and the manifest has a scheme-only filter for `${oauthRedirectScheme}`.
  - iOS: `ASWebAuthenticationSession` (`IosAppGraph.kt`) takes the callback itself. There are no URL types and no entitlements.
  - Web: `main.kt` reads `?state&code` on `$origin/` and calls `replaceState(pathname)`.
- **Two channels already open a screen from outside the UI.** `RecordScreenRequests` and `AccountScreenRequests` are AppScope `SharedFlow(replay=1)` channels. Two places consume them: `App.kt` `SessionContent` (in `LaunchedEffect`s) and `SessionHost.whileOpen`. Both push onto the tab currently shown.
- **The two shells navigate differently.**
  - The Compose shell has one `SaveableBackStack`, and `AppShell` switches tabs with `resetRoot(dest.screen(viewerDid), save, restore)`.
  - The presenter host keeps a `TabStack` per `TopLevelDestination` and exposes `goTo`, `pop`, `popTo` and `resetRoot`. The iOS `NavigationStack` path follows `shown.pushed`.
- **Screens are `@CircuitSerializable`, keyed by AT-URI strings, DIDs or handles, and enums.** `AtUri` (`core/lexicon` `Identifiers.kt`) splits out the authority, collection and rkey. `IdentityResolver.resolveHandle` turns a handle into a DID.
- **The AppView already serves `/.well-known/did.json` and `/oauth/client-metadata.json`.** Its public host is the host of `nativeOAuth.appviewPublicUrl`, which differs per developer (a tunnel). The OAuth scheme is derived from it.
- **The web dev server already serves `index.html` for any path** (`historyApiFallback`). However, `index.html` loads `webApp.js` by a relative path.

## Goals / Non-Goals

**Goals:**
- One grammar, in one module, that both shells and all three platforms use, in both directions (URL to screen and screen to path). The second direction is needed for web history and for later share links.
- Links reach the stacks through a single channel, which replaces the two ad-hoc request channels.
- A test fails when a screen is added with no path and no exclusion.
- The default iOS build (ad-hoc signed, no team) stays exactly as it is.

**Non-Goals:**
- Share or copy-link UI.
- Desktop links (desktop compiles against the shared code but registers no handler).
- A production web host or production OAuth client for the web.
- Deferred deep links (a link opened before the app is installed).

## Decisions

### A dedicated `:app-links` module, routes listed centrally
`app/links` is a KMP library with no Compose and no Metro. It depends on `:core-lexicon`, `:app-common-api` and every feature `-api`, and holds:
- `AppLinks(host)`, which parses a URL into an `AppLink` (path segments and query);
- `LinkRoute`s, one file per feature under `routes/`;
- `AppLinkRoutes.all`, `excluded` and `topLevelPrefixes`;
- `pathOf(screen)`;
- `LinkPlan(tab, chain)` and `StackEdit.of(current, chain)`.

Parsing must work without a graph or session: signed out, in `MainActivity.onCreate`, at web start-up, and in plain JVM tests. Route order must also be explicit.

*Alternatives considered:*
- **Routes in each feature's `-api`, collected by Metro multibinding.** Rejected. It grows the Swift-exported surface of every contract module, needs a graph before anything can be parsed, and leaves the matching order implicit.
- **Routes inside `:app-host`.** Rejected. It mixes pure parsing into a Compose-runtime module and makes the routes harder to test in isolation.

The module's path ends in none of `-api`, `-presenter` or `-ui`, so `checkModuleBoundaries` treats it as "Other". The boundary check gains a rule: `:app-links` may depend only on contracts, `-api` modules and the lexicon (see the `dev/module-boundaries` delta).

### Ktor's URL types for parsing and encoding
`io.ktor.http.Url`, `URLBuilder` and `encodeURLPathPart` handle percent-coding. That matters for DIDs: `did:web:localhost%3A8081` contains `:` and `%`. The library is already in the build through `:core-atproto` and covers every target. A `ktor-http` catalog entry is added for direct use.

*Alternative:* hand-written splitting. Rejected under the "prefer libraries" rule; percent-coding is easy to get wrong.

### Canonical paths: bsky-like under `/profile`, globally keyed places
An athlete's own records hang off their profile: `/profile/{actor}/activity|post|gear|batch/{rkey}`. Shared places such as `/segment/{did}/{rkey}`, `/trail/…` and `/dive-site/…` are top-level, because they're thought of as places rather than as someone's posts. Settings pages nest under `/settings`, mirroring how they're opened.

The full table lives in `AppLinkRoutes` and is pinned by the round-trip samples. Owner-only forms whose screen holds a bare `rkey` (gear and collection forms) always mean the viewer's own records. Arguments that are hints and not identity (`suggestOnOpen`, display `name`) are left out of the path, and the canonical screen sets them to their defaults.

### Handles resolved at plan time, not parse time
`parse` is synchronous and pure. `LinkOpener.plan` runs in `SessionScope`, resolves a handle authority with `IdentityResolver.resolveHandle`, and then builds `AtUri.of(did, Nsid.X, rkey)`. If resolution fails, it falls back to `ProfileScreen(handle)`, which already accepts handles and reports "not found". The same step applies the owner-only rule, which needs the viewer's DID.

### Link plans: tab + chain, applied as "keep then push"
Each route gives its screen's parent chain. `LinkPlan.tab` is a small `LinkTab` enum: `Feed`, `Search`, `You`, or `Current`. `:app-host` maps it to `TopLevelDestination`, so `:app-links` doesn't depend on the host.

`StackEdit.of(current, chain)` works like this: if the stack already holds `chain[0]`, keep the stack up to its last occurrence and push the rest; otherwise push the whole chain. This is how `AccountScreenRequests` behaves today, generalised. Both shells apply the same `StackEdit`.

- The host sets `selected` directly, because `select()` pops the tab to its root on reselect.
- The Compose shell uses a `selectTab(…, toRoot = false)` extracted from `AppShell`.

### One `LinkRequests` channel, a `StateFlow<AppLink?>`
`LinkRequests` is AppScope. It holds `pending: StateFlow<AppLink?>`, `request(url): Boolean` (returning false when the URL isn't ours) and `handled(link)`, which compares and sets so that a newer link arriving mid-plan isn't lost.

A `StateFlow` is used rather than a replaying `SharedFlow`, so that "a link is waiting" can be observed. The web needs that to mirror the pending link into `sessionStorage` across the OAuth redirect, and the history sync needs it to wait for the first link before writing any history.

Consumers run only while `SessionMode.Open`, so a link waits through sign-out, restoring and leaving without any extra state. The notification taps become `request("/record")` and `request("/settings/account")`, and the two old classes are deleted.

### Android: App Links on the link host, a separate fixed scheme
- `NativeOAuthConventionPlugin` exposes `linkHost`, the host of `appviewPublicUrl`, writes `LINK_HOST` into `GeneratedNetworkConfig`, and the build sets a `linkHost` manifest placeholder.
- The manifest adds an `autoVerify` https filter with one `pathPrefix` per top-level prefix, so `/oauth` and `/xrpc` stay in the browser, plus an `activitytracker` scheme filter. The OAuth filter is unchanged.
- `MainActivity.handleLink` runs after `handleOAuthRedirect` in both `onCreate` and `onNewIntent`. It skips intents launched from history, so Recents doesn't replay a link.
- A test keeps the manifest's prefixes equal to `topLevelPrefixes`.

*Alternative:* claiming the whole host. Rejected, because the browser must keep showing client metadata and other AppView endpoints.

### AppView association files from configuration
`AppViewConfig` gains `ANDROID_APP_PACKAGE` (defaults to the app id), `ANDROID_CERT_SHA256` (a list) and `APPLE_APP_IDS` (a list). `wellKnownRoutes` serves `assetlinks.json` and an AASA that excludes `/oauth/*`, `/xrpc/*` and `/.well-known/*`. Each returns 404 when unconfigured.

The local stack passes the values through Gradle properties, and `scripts/dev.sh` fills in the debug keystore's fingerprint.

### iOS: custom scheme always, Universal Links only with a team
- `iOSApp.swift` adds `.onOpenURL` and `.onContinueUserActivity(NSUserActivityTypeBrowsingWeb)`, both calling `IosApp.openLink(url: String, fitDecoder:)`. That function takes and returns only plain values Swift export carries.
- `Info.plist` gains `CFBundleURLTypes` for `activitytracker`. This is distinct from the OAuth scheme, which `ASWebAuthenticationSession` handles itself.
- Associated domains need a development team. Instead of adding one to the project:
  - the target gets an `App.xcconfig` with `#include? "Local.xcconfig"`;
  - `Local.xcconfig` is gitignored and written by `dev.sh` only when `activitytracker.appleTeamId` is set;
  - it sets `DEVELOPMENT_TEAM`, `APP_LINK_HOST` and `CODE_SIGN_ENTITLEMENTS` pointing at an entitlements file with `applinks:$(APP_LINK_HOST)?mode=developer`.

  Without it, the build matches today's.

### Web: absolute assets, start-up link, and history sync from a pure diff
- **Absolute assets.** `index.html` loads `/webApp.js`, and Compose resources are mapped to `/`-rooted paths, so nested URLs load.
- **Start-up link.** At start-up, `main.kt` requests `pathname + search` when it isn't `/`. While a link is pending, it is mirrored into `sessionStorage`, and the OAuth callback branch requests it back.
- **History sync.**
  - `App(…, backStackSync)` gets a hook point; desktop and Android pass nothing.
  - A pure `HistorySync.diff(old, new)` in composeApp `nav/` maps two stack snapshots to `Push(n)`, `Pop(n)`, `TabSwitch` or `Replace`, and is tested on the JVM.
  - `webApp`'s `BrowserHistorySync` applies the diff:
    - first frame: `replaceState` for the root, then `pushState` for each record above it;
    - push: `pushState`;
    - in-app pop: `history.go(-n)`, with a `pendingPops` counter so the resulting `popstate` is ignored;
    - tab switch: `pushState`;
    - replace: `replaceState`.
  - Each history entry's state carries the record key and tab. On `popstate`, the sync switches tab if needed and then pops to that key. If the key is gone (a forward entry, or a replaced record), it requests the URL as a link.
  - An `expected` stack stops the sync's own edits from echoing back into history.
  - On dispose it resets the URL to `/`.

*Alternative:* hash routing. Rejected, because paths are what the https links use, and the dev server already falls back to `index.html`.

### Display-only names become optional
`SegmentLeaderboardScreen`, `SegmentMayorsScreen`, `DiveSiteRegularsScreen` and `ImportBatchScreen` take `name: String? = null`. When it's null, the presenter loads the name: the segment's current name from `getSegment`, the site's from `getDiveSite`, or the batch athlete's from the profile. In-app callers still pass the name, so in-app behaviour has no extra fetch and no title flash.

*Alternative:* dropping `name` everywhere. Rejected, because it adds a fetch and a blank title on every in-app open.

## Risks / Trade-offs

- [`snapshotFlow` may see a tab switch's `resetRoot` with restore as several changes] → `diff` falls back to `Replace` when the change isn't a clean push, pop or switch. A JVM test covers the switch-and-restore sequence.
- [`history.go` and `popstate` are asynchronous, so the sync could misread its own pop as the user's] → the `pendingPops` counter and the `expected` stack. JVM tests cover the pure core, and a manual browser check covers the rest.
- [Compose resources under nested URLs on wasm] → map resource paths to absolute paths, and verify in the browser pane on a nested URL before relying on it.
- [Universal Links can't be verified without a paid team and a public HTTPS host] → the custom scheme covers development. `?mode=developer` lets a team-configured simulator skip Apple's CDN.
- [The link host changes per developer] → links shared between developers won't open each other's apps. That's acceptable until a production host exists, and the custom scheme is host-independent.
- [A link to a private record the viewer can't see] → the page shows its usual not-available state. Links reveal nothing the page wouldn't already show.

## Migration Plan

There's nothing to migrate.
- New AppView settings default to unset (404).
- The Android manifest adds filters and removes none.
- Retiring the two request classes changes no behaviour; their notification taps become link requests.

Rollback is reverting the change.
