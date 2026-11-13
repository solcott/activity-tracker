# Proposal

## Why

Round 4 of moving rule-chosen text into shared code (the roadmap's "Other" item). A few texts are still put together by each UI: two
forms' public notes are sentences each UI joins with a space, Account's end dialog is paragraphs each joins with a blank line, Account
and Leave draw bullets each in their own way (Leave's deleted and stays lines are even listed in each UI), the iOS profile names a
country inline rather than through the lookup Edit profile uses, and the iOS activity page writes its start in metric where the import
page uses the athlete's units. One more was unchecked: whether the iOS FIT decoder's English failure text reaches a screen.

## What Changes

- **Sentences and paragraphs**: `AppText.sentences(texts)` and `AppText.paragraphs(texts)` (`:app-common-api`) join a list once, through
  shared strings (`text_sentence_pair`, "%1$s %2$s", and `text_paragraph_pair`, a blank line between), as `AppText.joined` does a list
  of names. `CreateSegmentTexts.publicNote` and `CreateDiveSiteTexts.publicNote` return the joined note, and `AccountTexts.endMessage`
  the end dialog's message (it replaces `endLines`); both UIs show them as they are.
- **Bullets**: a bullet's mark is one shared string (`LabelText.bullet`, `text_bullet`), and each list's lines are Kotlin's:
  `AccountTexts.missingFeatures` (as now), `LeaveTexts.deviceLines` (as now), and new `LeaveTexts.deletedLines` and
  `LeaveTexts.staysLines(handle)`. Each UI draws a list of lines one way, the mark and then the line with a hanging indent: one
  `Bullets` composable for Account and Leave, one `BulletList` view for `AccountView` and `LeaveView`.
- **Country names**: `ProfilePage` names the athlete's country through `CountryNames.name(of:)`, which no longer needs the whole list
  built.
- **The start figure**: the iOS activity page passes the athlete's units (`stats.units`) for its start, as the import page does.
- **FIT failures**: checked, no code change. The iOS decoder's messages reach no screen: an import's failure is a `UiError`
  (`ReadActivityFile`), whose text never includes the failure's message, and a batch's skipped file a `SkipReason`.

Visible text changes:
- Android, desktop and web: Account's missing-features lines and Leave's bullets wrap under their line, not under the mark.
- iOS: Account's missing-features lines wrap under their line, not under the mark. The activity page's start is unchanged (a start has
  no unit).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `dev/shared-display-text`: sentences, paragraphs and bullets are put together once, and a profile's country is named the way Edit
  profile names it.

## Impact

- `:app-common-api` (`AppText`, `LabelText`, `strings.xml` in `:ui-resources`), `:feature-segment-api`, `:feature-divesite-api`,
  `:feature-settings-api`, with tests.
- Compose: `CreateSegmentUi`, `CreateDiveSiteUi`, `AccountUi`, `LeaveUi` (and a `Bullets` composable in `:feature-settings-ui`).
- Swift: `CreateSegmentView`, `CreateDiveSiteView`, `AccountView`, `LeaveView`, a `BulletList` view, `ProfilePage`, `EditProfileLogic`,
  `ActivityPage`, with text tests.
- Android references and iOS 26.0 snapshots of Account with an outdated grant and of Leave, where a bullet line wraps.
- Roadmap `openspec/explorations/ios-native-ui.md`: the item ticked and a findings section.
