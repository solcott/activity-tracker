# Proposal

## Why

Every iOS screen is now native SwiftUI over the shared presenters, so each place where a Compose composable or a Swift view picks its
own text by a rule is a place where Android and iOS can drift apart. The roadmap's open item ("About 120 composables still choose text
for themselves") has no list, so later rounds can't pick up where one left off. This first round makes that list and moves its first
group, dates and times. A few dates are already chosen in shared code but written as ISO text ("2026-07-01") while every other date the
app writes out reads "Jul 1, 2026".

## What Changes

- **Inventory.** The roadmap's item becomes a checklist of every place in the Compose UI modules (`feature/*/ui`, `app/common/ui`,
  `ui/designsystem`) and the Swift views (`iosApp/iosApp`) that still chooses text by a rule. It is grouped by area (dates and times;
  charts; stats and units; dive tables and dead dive code; feature labels; other), with file:line and what the other UI does.
- **Every date a UI shows is written out the same way.** The gear form's acquired date, "Mounted on … since", "Retired …", the gear
  page's mount dates ("Since …", "… – …"), the feed filter's custom range chip, the upload queue's unnamed activities ("Ride, …") and
  edit profile's birth date read "Jul 1, 2026" in both UIs, instead of "2026-07-01". The shared date (`DateText`) gains a form that takes
  a calendar day, and threshold history's short date ("Mar 2") moves into it as `DateText.monthDay`.
- **Edit profile's birth date is chosen in shared code.** Both UIs ask for it. Compose now shows the written-out date, not the stored
  text.
- **The combine picker's row and a lift's rides and time are the shared texts in Compose.** The picker row is `CombinePickerTexts.detail`,
  and the lift row is `ActivityPageTexts.liftRides` and `liftTime`. The native views already ask these.
- **iOS's segment leaderboard asks Kotlin for an entry's time** (`SegmentBoardTexts.time`), as the Compose board does.
- **iOS's date pickers stop at today on the shown clock.** Birth date, gear acquired and the feed's custom range use the clock that
  previews and snapshots fix, as the Compose pickers use `LocalClock`. They no longer use the device's own clock.
- **Dead date code goes.** The unused `monthName` and composable `longDate` in `:app-common-ui` are removed.

Visible text changes, on Android, desktop, web and iOS alike: the gear, feed filter, upload and birth-date dates above. Nothing else changes on
either platform.

Out of scope: the other inventory groups. That includes the activity charts' time axes and readouts, which move with the Compose charts
onto `CardLines` in the charts round. It also includes how each UI formats a time of day (`AppText.Time`), which follows the device's
clock by design.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `dev/shared-display-text`: a written-out date covers every date a UI shows (gear, the feed filter's range, uploads, birth date), and a calendar day reads the
  same as a `YYYY-MM-DD` text.
- `ui/settings-layout`: the iOS gear form's mounted-on line reads its date written out.

## Impact

- `:app-common-api` (`DateText`), `:feature-gear-api` (`EditGearTexts`, `GearPageTexts`), `:feature-uploads-api` (`UploadsTexts`),
  `:feature-profile-api` (`EditProfileTexts`), `:feature-feedfilter-api` (`FeedFilterTexts`), `:feature-settings-api`
  (`ThresholdHistoryTexts.shortDate`). These come with tests.
- Compose: `EditProfileUi`, `CombinePickerUi`, `ActivitySegmentsUi`, and `HistoryDates.kt` (removed).
- Swift: `EditProfileView`, `EditGearView`, `FeedFilterBar`, `SegmentLeaderboardView`, `ShownClock`, and the Swift text tests that
  assert ISO dates.
- Android screenshot references and iOS snapshots that show those dates.
- Roadmap `openspec/explorations/ios-native-ui.md`: the inventory checklist and this round's findings.
