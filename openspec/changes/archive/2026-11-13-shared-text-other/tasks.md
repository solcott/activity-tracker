# Tasks

## 1. Shared text

- [x] 1.1 `AppText.sentences` and `AppText.paragraphs` with `text_sentence_pair` and `text_paragraph_pair`; `LabelText.bullet` with `text_bullet`; tests
- [x] 1.2 `CreateSegmentTexts.publicNote` and `CreateDiveSiteTexts.publicNote` return the joined note; `AccountTexts.endMessage` replaces `endLines`; tests
- [x] 1.3 `LeaveTexts.deletedLines()` (a function: the -api's strict stability check rejects a `List` val) and `staysLines(handle)`; tests and the text-name tests

## 2. Compose

- [x] 2.1 `CreateSegmentUi`, `CreateDiveSiteUi` and `AccountUi`'s end dialog show the joined texts
- [x] 2.2 A `Bullets` composable (mark, hanging line) that `AccountUi` and `LeaveUi` draw, over the Kotlin lines; a UI test that the paragraph string has its blank line

## 3. Swift

- [x] 3.1 `CreateSegmentView`, `CreateDiveSiteView` and `AccountView` show the joined texts
- [x] 3.2 A `BulletList` view that `AccountView` and `LeaveView` draw, over the Kotlin lines and `LabelText.bullet`
- [x] 3.3 `ProfilePage` names the country through a static `CountryNames.name(of:)`; `ActivityPage`'s start uses `stats.units`
- [x] 3.4 Swift text tests updated for the new functions

## 4. Pictures

- [x] 4.1 Update and look at the Android references that change: Account's `noSpaceScope`, `outdated`, `signInAgainPending` and every Leave summary (bullets now hang)
- [x] 4.2 Rerecord and look at the iOS 26.0 snapshots that change: Account's `ending`, `noSpaceScope`, `outdated`, `signInAgainPending`, light and dark (the gap after the mark)

## 5. Verification

- [x] 5.1 `./gradlew check -Pkotlin.incremental=false` passes
- [x] 5.2 An ad-hoc-signed iOS Release build passes
- [x] 5.3 `xcodebuild test` passes on an iOS 26.0 simulator clone: 454/454
