# Design

## Context

The host runs a shown record's sub-screens (`HostRecord.sub(screen, outerEventSink)` → `HostSubRecord`, dev/presenter-host), and
`SubScreenView` draws one from Swift. Only `FeedFilterBar` was drawn that way, in no page. A sheet sub-screen differs: it shows only
while open, so the UI must close its sub-record when it goes (`HostSubRecord.close()`), or an equal sheet reopens with the old state.

Report is carried by a host as `Reporting` (`:feature-report-api`): `available`, the open `sheet: ReportScreen?`, the `confirmBlock`
offer once taken, and a sink (`Open`, `Sheet(outer)`, `ConfirmBlock`). `rememberReporting` (`:app-common-presenter`) runs it, and
`ReportingHost` (`:app-common-ui`) draws it in Compose: a bottom sheet over `SubCircuitContent`, and the Block alert. The profile, the
feed, posts, an activity's page and the gallery carry one. The shared activity card has no menu and offers no Report: an activity is
reported from its page.

The place-name sheet (`ProposePlaceNameScreen`) is carried by `PlaceNameFields` the same way (`sheet`, `confirmDelete`, a sink), drawn by
`PlaceNameDialogs` on the segment and dive site pages, both still the shared UI's.

Native pages are given only their state (`ScreenRegistry`'s closures take the state), not the record.

## Goals / Non-Goals

**Goals:** a native report sheet a native page shows from its `Reporting`, first on the profile; a way in other native pages (the post
card) reuse unchanged; the place-name sheet native the same way; every word and condition chosen in Kotlin.

**Non-Goals:** the post card's Report (`ios-native-post-card` wires it), a native segment or dive site page, Report on the activity card
(neither UI offers it), changing what the sheet sends.

## Decisions

1. **The record comes from the environment.** `NativeScreen` sets `\.hostRecord` to its record; a sheet modifier deep in a page reads it.
   *Alternative:* registry closures taking the record, and each page passing it down. Every registration would change for the few
   pages with sheets, and the two sessions editing `ScreenRegistry.swift` would conflict. Rejected. Outside the host (previews,
   snapshots) there is no record: the modifier then draws nothing, and a snapshot draws the sheet's view from a sample state directly.
2. **A modifier per carrier: `reporting(_:)` and `placeNames(_:)`.** Each shows SwiftUI's own `.sheet` while the carrier's `sheet` is
   set, drawing `SubScreenView` over it, and the carrier's alert (Block, delete). A post card or any page with `Reporting` adds the same
   modifier: the way in for `ios-native-post-card`.
3. **The modifier keeps the screen it showed.** It holds the shown screen in `@State` from when the carrier opens it, so the sheet goes on
   drawing it while it slides away after the carrier cleared it, and in `onDismiss` it closes that screen's sub-record
   (`record.sub(screen).close()`, which finds the held one). A swipe down sends the carrier's own close (`Sheet(Closed)`), as the
   Compose sheet's `onDismiss` does.
4. **The Block confirmation waits for the sheet to go.** Taking the offer clears the sheet and sets `confirmBlock` in one state; SwiftUI
   doesn't present an alert from a view whose sheet is still dismissing. The alert shows once `onDismiss` has run.
5. **The sheet's words and choices are Kotlin's.** `ReportTexts` (`:feature-report-api`): the title with the author, the reasons as a
   list of `ReportReasonChoice` objects (a collection of enums can't reach Swift; dev/ios-swift-interop) each with its label, the note's
   label (required for Other) and limit, the private note, Send, the thanks, Block offer, and the Block confirmation's title and message.
   `ReportState.noteRequired` and `chose(reason)`. `ProfileState.reportEvent(name)` builds the profile's `ReportingEvent.Open` (an
   account target, Block offered unless blocking) when Report is offered, so Swift builds no `:core-data` `ReportTarget`. The Compose sheet,
   `ReportingHost` and profile ask the same. `ProposePlaceNameTexts` (`:app-common-api`) likewise: title, confirm, lead line, field,
   invalid, share switch and detail, limit note, share text label and too long; `ProposePlaceNameState.showsInvalid` and `shareEnabled`.
6. **Native layout.** The report sheet is a grouped form in a navigation stack: the reasons as rows with a checkmark (iOS lists a single
   choice that way; the shared sheet's chips are Material's), the note as a multi-line field, the private note under it, the error,
   Cancel and Send in the bar (Send disabled until `canSend`, a spinner while sending). Sent, it shows the thanks, Block {name} and Done.
   The name sheet is a form too: the lead line, the name field, Share to feed as a toggle row, the audience as a picker row (trailing),
   the limit note and the text. Both use the design system's `AppTextField` over the states' text fields.

## Risks / Trade-offs

- [A page shows the modifier without a record in its environment] → nothing shows: the carrier's `sheet` stays set. Only native pages
  drawn by `NativeScreen` carry a record; the rule file says a sheet modifier needs one.
- [The post card's change also edits `ProfilePage.swift`] → this change only adds a menu button and a modifier line there.
