# Design

## Context

What exists, from the code:

- **The contracts** (`feature/settings/api`).
  - `AccountState` holds:
    - `handle`, `did` and `pdsHost` as strings;
    - `grant: GrantStatus`, a sealed interface of `:core-data`: `Current`, `SpacesUnsupported`, or `Outdated(missing: Set<GrantFeature>)`, a set of enums;
    - `confirming: EndingSession?`, holding `signInAgain` and a `PendingWork` of changes, `importSaving` and `exportPending`;
    - `ending`;
    - `export: DataExportStatus`: `None`, `Running(records, filesDone, filesTotal, activitiesDone, activitiesTotal)` with a `phase` getter, `Ready(archive: ExportArchive, problems, unsentChanges, spacesIncluded)`, `Paused(neededBytes: Long?)`, or `Failed`;
    - `askingKeptShared` and `exportError: UiError?`.
  - `AccountEvent` holds `Back`, `SignOut`, `SignInAgain`, `Confirm(signInAgain)`, `Dismiss`, `Export`, `CancelExport`, `ExportSaved`, `ExportShared`, `ExportSaveFailed`, `KeptShared(kept)` and `Leave`.
  - `LeaveState` holds:
    - `handle` and `confirmHandle`, a `TextFieldState`;
    - `places: SharedPlacesSummary`: `Loading`, `Loaded` with four counts, or `Failed(error)`;
    - `pending: PendingWork?` and `recording`;
    - `export: LeaveExport`, an enum of `None`, `Running` and `Ready`;
    - `needsSignInAgain`, `error: UiError?` and `starting`;
    - `run: LeaveRun?` of `step: LeaveStep?` (an enum of `:core-data`), `recordsDeleted`, `pausedUntil: Instant?` and `error`;
    - the getters `handleMatches` and `canLeave`.
  - `LeaveEvent` holds `Back`, `Export`, `CancelExport`, `SaveExport`, `SignInAgain`, `Leave` and `Retry`.
  - `LeaveScreen(resume)` is a `SettingsPage`.
- **The shared UIs build text in composables.**
  - `AccountUi`:
    - maps each `GrantFeature` to a string and lists the missing ones in `GrantFeature.entries` order, each with "• ";
    - picks the export's running line by `phase`, with a records plural;
    - rounds `neededBytes` up to whole MB inside the paused line;
    - joins the end-session alert's lines with blank lines.
  - `LeaveUi`:
    - filters the four place counts into plurals, or shows the "none" line;
    - builds the device lines;
    - maps `LeaveStep` to its line;
    - formats `pausedUntil` with `timeFormat()` (`:app-common-ui`), which follows `uses24HourClock()`.
- **Saving an export on iOS** is `rememberArchiveHandOff`'s iOS actual (`:app-common-ui`):
  1. It writes the zip into FileKit's cache folder with `ExportArchive.writeTo(PlatformFile)` (`:core-data`, `nonWebMain`).
  2. It presents a `UIActivityViewController` and waits for its completion handler.
  3. It deletes the file and waits for modals to close.
  4. It reports `Shared` when an activity ran, or `Dismissed`.

  `FileKit` isn't exported to Swift.
- **The host.** Account and Leave are `SettingsPage`s, so in the split Leave opened from Account is a deeper page: `SettingsSplit.first` is Account and `page` is Leave. Each record's `HostRecord.paneRoot` says whether it is the split's first page. A leave in progress takes the session's mode to `Leaving`, and `SessionView` draws `session.leaving`, a `LeaveScreen(resume = true)`, as a `SingleScreen` with no stack.
- **The iOS shell.**
  - `pageTitle` draws `PageBar` in the column, with no back button. Alone it draws the stack's bar.
  - `AppTextField` and `TextFieldModel` follow a `TextFieldState`.
  - `AppListRow` takes a title, a summary, a trailing control and `selected`.
  - `ErrorMessage` draws a `UiError`.
- **Samples.** `AccountSamples.all` and `LeaveSamples.all` in `:app-fixtures` already cover almost every state.
  - Account: outdated, no space scope, spaces unsupported, pending uploads and import (the end-session question), each export status, and the kept-shared question.
  - Leave: confirmed and wrong handle, places loading, failed and none, pending work, an export running and ready, sign in again, unreachable, starting, resuming, each step, rate limited, and stopped.

## Goals / Non-Goals

**Goals:**

- The two pages, native, saying and offering what the shared pages do, alone, beside the list and, for Leave's progress, in place of the app.
- A back button in the column header, the one remaining open item from `ios-native-settings-pages`.
- The first native file hand-off and the first time of day, each in a shape later screens reuse.
- Every rule these pages follow written once in Kotlin.

**Non-Goals:**

- `LeftElsewhereScreen`, the screen shown when the athlete left on another device: it shares the leave's words but isn't a settings page, and it is drawn by `SingleScreen`'s fallback until a change takes it.
- Moving `timeFormat()` and the other date and time composables: only the one time this page shows moves.
- A Swift wrapper for alerts: SwiftUI's `.alert` is the platform's, as `adaptive-ui.md` says.

## Decisions

### 1. Probe first

Run these before the pages are designed in detail, as each phase 4 change has, with throwaway Swift tests on the iPhone 17 simulator (iOS 26.0) and by reading the generated Swift. Record them in the roadmap under "Phase 4 probes (account)":

- **`GrantStatus`, `DataExportStatus`, `SharedPlacesSummary`, `LeaveRun` and `EndingSession` read in Swift.**
  - These are sealed types and data classes of `:core-data` and the contract, cast through `sealedType()` or `as?`, as `LoadStatus` is.
  - Check that `PendingWork`, `LeaveStep` and `DataExportStatus.Running.Phase` arrive as Swift types and enums, since a contract signature names them.
  - Check whether `ExportArchive` arrives as a class, since `Ready` names it.
  - Expect `Outdated.missing` (a `Set` of enums) and `pausedUntil` (`kotlin.time.Instant`) not to arrive: confirm which.
- **An entry Swift awaits.** `IosApp.writeArchive(archive: ExportArchive, path: String)` writes the zip and reports whether it did.
- **`List<AppText>` reads in Swift** as `any AppText` elements, from a function rather than a getter (`ios-native-settings` found a `List` getter returning an immutable list crashes).

**What the probes found** (the roadmap's "Phase 4 probes (account)"):
- The sealed states all cast with `as?`, and the classes and enums arrive typed.
- `ExportArchive` arrives with its `name`, and `Instant` with `epochSeconds`.
- A function's `List<AppText>` reads as `[any AppText]`.
- Reading `Outdated.missing` stops the app (a set of enums, as a list of them did), so the missing features reach Swift as text (decision 2).
- A `suspend` entry is generated as `async throws` and was awaited from Swift, so `writeArchive` is a `suspend fun` returning a `Boolean`, not the callback first planned.

If a probe fails, the decision that rests on it changes before any page code is written, and the change's artifacts are updated.

### 2. Kotlin chooses the text: `AccountTexts` and `LeaveTexts`

They go in `:feature-settings-api` beside `SettingsTexts`, returning `AppText`:

- **`AccountTexts`:**
  - `signIn(grant)`: the status line;
  - `missingFeatures(grant)`: one `AppText` per missing feature, in `GrantFeature.entries` order, empty unless outdated. This covers the enum set Swift can't read, and the "• " stays a layout choice of each UI.
  - `exportLines(export)`: the status lines under the export's heading, including the running line by phase and the paused line with its megabytes;
  - `endTitle(ending)`, `endMessage(ending)` (the lines joined) and `endAction(ending)`.
- **`LeaveTexts`:** `places(summary)` (the lines, or "none"), `deviceLines(state)`, `step(run)`, `recordsDeleted(run)` and `paused(run)`.
- **State getters** cover the conditions both UIs test: `AccountState.canEnd` (`!ending`) and `canOfferSignInAgain`; `LeaveState.showsDeviceSection`, `runCanGoBack` (`run == null`) and `showsLeaveButton`.

The Compose pages resolve them, and their screenshot references stay unchanged, which is the check. The web-only "keep this tab open" line stays a `UiPlatform` choice in `AccountUi`, since iOS never shows it.

### 3. A time of day is an `AppText`

`AppText.Time(epochSeconds: Long)` is a new case: a moment shown as a time of day on the device's clock. Each `TextSource` formats it:

- Compose with `timeFormat()` in the local time zone, as `LeaveUi` does now;
- Swift with `Date(timeIntervalSince1970:).formatted(date: .omitted, time: .shortened)`, which follows the device's 12- or 24-hour setting.

`LeaveTexts.paused(run)` is `Named("leave_paused", [Time(...)])`. This satisfies both "chosen by shared code" and "reaches Swift as a value it can format" with no `Instant` in a Swift signature.

Alternative rejected: an `epochSeconds` getter and Swift building the sentence. That puts a rule-bound sentence's assembly in Swift.

The sealed `AppText` gains a case, so every `when` over it and Swift's `TextSource` switch must handle it. Their tests in `AppTextTest` and `SettingsListsTextTests`' style cover a 12- and a 24-hour clock.

### 4. Saving: Kotlin writes, Swift shares

- **Kotlin.** `IosApp.writeArchive` writes the archive with `ExportArchive.writeTo` to the path Swift gives. It is a `suspend fun`, which Swift awaits. It replaces an earlier file, deletes a partial one on failure, and returns whether the zip was written.
- **Swift.** `ArchiveShare` in `Screens` (it is Account's alone) then:
  1. picks the cache-folder path, `FileManager`'s caches URL plus `archive.name`;
  2. awaits the write;
  3. presents `UIActivityViewController` from the key window's top view controller, anchored for the iPad popover, and awaits its completion handler;
  4. deletes the file;
  5. sends `ExportShared` when an activity ran, `ExportSaveFailed` when writing failed, and nothing when dismissed.

  The `KeptShared` alert then shows from `askingKeptShared`, after the sheet has gone, as the shared actual waits for modals to close.
- **No `ShareLink`.** It doesn't report whether an activity ran, which `auth/data-export` needs.

### 5. Back in the column header

- **`pageTitle(_:back:canGoBack:actions:)`.**
  - `back` is the page's own way back, for example `{ state.eventSink(LeaveEvent.Back) }`, as the shared top bar's `onBack` is. Only a page that can be opened deeper than the column's first passes one.
  - `canGoBack` false means the page can't go back now: no back button in the header, and alone none in the stack's bar.
  - A required no-op default was rejected: it would draw a back button that does nothing on a deeper page that forgot to pass one.
- **`ScreenView`.** It gives a native screen `\.paneRoot` from `record.paneRoot`, followed like the state.
- **In the column**, `PageBar` shows the back button when `!paneRoot` and `back` is set (and `canGoBack`).
  - It is a chevron-only button at the leading end, `Image(systemName: "chevron.backward")` in the bar's tint, with the accessibility label Back.
  - The title's horizontal padding becomes the larger of the leading and trailing needs, so it stays centred.
- **Alone**, the stack's back button pops the Kotlin stack as it always has. When `canGoBack` is false the page hides it with `.navigationBarBackButtonHidden(true)`, which also turns off the edge swipe.
- **In place of the app**, the resumed Leave is a `SingleScreen` with no bar, and its run has no back.

Alternative rejected: the header popping the host's stack directly. The shared pane sends the page's Back event, and a page that refuses to go back (a running leave) must be able to say so.

### 6. The pages

- **`AccountView`.** A `List` of inset grouped sections, in the shared page's order: identity, Sign-in, Password and security, Your data, Sign out, Leave.
  - Copy uses `UIPasteboard.general.string`, with a local `@State copied`.
  - Sign out and Leave are `AppListRow`s; Sign out is a plain button row.
  - The export's buttons are `AppButton` and `AppSecondaryButton` inside the section's row, as the shared page has them.
  - The two questions are `.alert(isPresented:)` driven by `confirming` and `askingKeptShared`, each with a destructive action and Cancel, sending `Dismiss` or `KeptShared(kept: false)` on cancel.
- **`LeaveView`.**
  - **Summary:** the intro as a header-less section's text, bulleted sections, places with progress while loading, the device section only when there is something to lose, the export, then the confirmation.
  - **Confirmation:** `AppTextField` (no autocapitalisation or autocorrection, `.URL` keyboard), the error, the progress while starting, Leave (destructive `AppButton`, disabled unless `canLeave`), and Cancel.
  - **Running:** only the progress section.

Both register in `ScreenRegistry` under `AccountScreen` and `LeaveScreen`. Both draw from type aliases in `KotlinTypes.swift`.

### 7. Samples and tests

- **Samples.** The existing ones serve both UIs. The two states they lack, Account `ending` and a failed save (`exportError`), go in a `variants` map beside `all`, so the shared screenshot set is unchanged.
- **Swift snapshots** at a phone's width, plus a few beside the list on an iPad size: Account, Leave as a deeper page, and Leave's progress.
- **Text tests** compare each Swift resolution with Kotlin's for every sample.
- **A `PageBar` gallery entry** with and without back.

## Risks / Trade-offs

- **A real leave deletes the account's data.** On a device it is walked to the confirmation and back only; its progress is seen in previews and snapshots from samples. Record that under "Not verified".
- **The share sheet** can be walked on the simulator to Save to Files and to dismissal. AirDrop and other apps can't. The iPad popover's anchor is the presenting view, so the sheet comes from the window's middle rather than from Save, as in the shared actual.
- **Two writers of the same file name.** The Compose actual and `ArchiveShare` both write `archive.name` into the cache folder, but only one UI draws Account at a time, so they never race.
- **`AppText` gaining a case** touches every resolver. The compiler finds the Kotlin ones; the Swift `switch` over `sealedType()` needs a default that fails a test rather than showing nothing.
- **iOS 18** can't be walked signed in, and the snapshots compare only on iOS 26.0, as before.
