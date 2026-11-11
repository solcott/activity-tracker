# Design

## Context

What exists, from the code:

- **The contract** (`feature/profile/api`, `EditProfileScreen.kt`). `EditProfileState` holds `displayName`, `description`, `locality`, `region`, `gender`, `weight`, `height`, `heightInches` as `TextFieldState`s the presenter owns; `avatar: AvatarImage?` (`Saved(url)` or `Picked(jpeg: ByteString)`); `country` (ISO code or empty) and `birthDate` (`YYYY-MM-DD` or empty) as `String`s; `units`; `visibility: DetailsVisibility` (`:core-data`, a nullable `Visibility` string per group); `approveFollowers`; `nowHidden: ImmutableList<DetailGroup>` (an enum of `:core-data`); `content: ContentState<Unit>`; `needsSignIn`; `saving`; `error`. Getters `loaded`, `valid`, `heightValid`; companion constants for the three maximum lengths. Events: `AvatarPicked(jpeg)`, `RemoveAvatar`, `CountryChanged`, `BirthDateChanged(value: String)`, `GenderChanged`, `VisibilityChanged(group, visibility)`, `ApproveFollowersChanged`, `Save`, `Retry`, `SignInAgain`, `Back`. The presenter pops on a successful save, so a native form needs nothing for closing. It is not a `SettingsPage`: the profile (still the shared UI's) pushes it on the tab's stack.
- **The shared form builds text and rules in composables** (`EditProfileUi`, about 500 lines): the counters (`edit_profile_length` with Kotlin's `length`), the units' labels, the now-hidden lines (`edit_profile_now_hidden` with the group's name), the gender menu (Not set, the three known values, Self-describe, with a UI-held "chose self-describe" flag and the rule that a saved value outside `Gender.all` is a self-description), the country field (`Countries.codes` with `countryName` in `:app-common-ui`, sorted by name, a typed code first and then names containing the typed text folded by `stripDiacritics`), the weight's validity (`WeightConversions.isValidInputText`), Save's condition (`loaded && valid && !saving`), the sign-in-again notice and button, and the avatar's compression (`compressAvatar`, FileKit's JPEG at 512 px and quality 85, two private constants).
- **What Swift can read**, from the generated Swift: `DetailGroup` is a Swift enum with `visibility(visibility:)`, and an event taking one (`VisibilityChanged(group:visibility:)`) has an initialiser; `nowHidden` arrives as an untyped `ImmutableList`, which can't carry an enum (an enum list stops the app; `ios-native-settings-pages`); `AvatarImage.Saved` and `.Picked` are classes to cast; the companion's constants are `Int32` getters.
- **The native pieces this reuses** (`ios-native-gear-editors`): `AppTextField` with `.row` and `.multiline` layouts, supporting text and an error state over a `TextFieldModel` with `TextFieldRules`; `AppPickerRow`; `pageTitle(_:actions:)`; `ContentStateView`'s form without a value; `ErrorMessage(layout: .row)`; `PhotosPicker` with `PhotoPick` (Kotlin's compression, a HEIC retried as UIKit's JPEG); `CalendarDay` and the date popover; a sheet with its own `NavigationStack`; `Avatar`, `RemoteImage`; `SignInAgainNotice` (`:app-common-api`), which the follow and invite lists ask for the same notice; `VisibilityTexts.followersNote`; `LabelText.visibility`, `gender`, `detailGroup`, `visibilityScope`.
- **Samples**: `EditProfileSamples.all` (loading, failed, loaded, an invalid weight), every group's visibility left null, no photo, no now-hidden group, metric.

## Goals / Non-Goals

**Goals:**

- The form native, saying, limiting and offering what the shared form does, laid out the way an iOS edit form is.
- Every rule written once in Kotlin, for both UIs.

**Non-Goals:**

- A change to what is saved, where, or when; an unsaved-changes prompt; a progress mark while saving or compressing (the shared form has none).
- A native image cropper or camera capture; the shared pick has neither.

## Decisions

### 1. Probe first

Throwaway code on a cloned iPhone 17 simulator (iOS 26.0), recorded in the roadmap under "Phase 4 probes (edit profile)" before the view is built on the answers:

- Every `EditProfileSamples` state reads from Swift; `DetailsVisibility`'s fields read as `String?`; `DetailGroup.visibility(visibility:)` and `VisibilityChanged(group:visibility:)` work with a Swift enum case; the companion's limits read.
- `AvatarImage.Saved` and `.Picked` built in Swift cast back with `as?`, and `Picked.jpeg` reads as a `ByteString`.
- A scratch `IosApp` entry over `compressAvatar` with a large JPEG, a HEIC and junk: what comes back (format, size), so `PhotoPick` knows whether its JPEG retry matters here.
- A Kotlin function taking and returning a plain `List` of a class, called from Swift with an array, empty and not (the country search's shape, decision 6).
- Not run: reading `nowHidden` with `elements(as:)`. A list of enums stops the app, already settled; the generated getter is untyped.

### 2. One grouped form, Save in the bar

A `Form` inside `ContentStateView`'s form without a value, titled with `pageTitle(Text(.editProfileTitle))`, Save as the bar's action while `loaded` (the gear finding: the shared Save is the form's last row, so the bar's needs the load condition), disabled unless `canSave`. Its sections, top to bottom:

1. A status row when there is a `SignInAgainNotice` (the failed save, or that the session must sign in again) with Sign in again when it offers it, where it is seen with Save in the bar.
2. The photo (decision 5).
3. Display name (`.row`) and bio (`.multiline`), each with its counter as supporting text, red when too long.
4. Approve followers, a `Toggle`, with its help as the footer.
5. A header-only "Details" with its help, then one section per group: Location (city, state or province, country), Birthdate, Gender, Height and weight. Each ends with an `AppPickerRow` labelled with the group's visibility scope ("Who can see your city, state and country"), named "Visible to" for VoiceOver, over the four visibilities; its footer holds the Followers note and the now-hidden warning for that group.

The whole form is disabled until loaded, as the shared fields are (`enabled = state.loaded`). Back is the stack's.

Rejected: Save as the last row (under the keyboard on a long form); the now-hidden warnings in one list at the end, as shared (a warning about the location belongs under the location, which is where the athlete just chose; the words, with the group's name, still stand alone).

### 3. Text fields as form rows, with the shared limits

One `TextFieldModel` per field in `@State`, with no rules: as on the shared form, typing past a limit is let through, the counter and the field turn red, and `canSave` holds Save back. A refusing rule (`TextFieldRules(maxLength:)`, as the gear form uses) was rejected: an edit is refused when its result is still too long, so a value saved longer than its limit by another app couldn't even be shortened a character at a time. The display name (`.row`) and bio (`.multiline`) show Kotlin's counters; city, state and the self-description show the error colour without a counter, as their limit (64) is far above what they hold. Height and weight use the number and decimal pads with no character rule, marked wrong by `heightValid` and `weightValid`. Imperial height is two rows, "Height (ft)" and a new "Height (in)", since the shared "(in)" only reads beside the feet field. The self-description is `.multiline`, as its label is too long to lead a row. Contract fields Swift can't read under their own name get a getter that it can: `description` reaches Swift as `bio` (decision 1's probe of the generated Swift).

### 4. Kotlin rules (no visible change)

- **`EditProfileLimits`** (`:feature-profile-api`): `displayName`, `description`, `detail` (the companion constants, kept), and the avatar's `avatarPixels` (512) and `avatarQuality` (85), moved from `EditProfileUi`.
- **`EditProfileTexts`**: `counter(text, max)` ("5/64", numbers through `formatWhole`), `weightLabel(units)`, `heightLabel(units)`, `inchesBeside` (the shared "(in)") and `inchesRow` ("Height (in)"), `nowHidden(group)`.
- **`GenderChoices`**: the menu's choices as tokens (`NOT_SET` "", the three `Gender` values, `SELF_DESCRIBE`, a token never sent), `label(choice)`, `selfDescribes(gender, chose)`, `chosen(gender, chose)` and `valueFor(choice, gender)` (what to send: a self-description keeps the words typed so far, or empty from a known value). The "chose self-describe" flag stays each UI's view state, since nothing is sent until words are typed.
- **Getters on `EditProfileState`**: `canSave`, `weightValid`, `displayNameValid` and `displayNameCounter`, `descriptionValid` and `descriptionCounter`, `detailValid(text)`, `visibilityOptions`, `visibilityOf(group)` (the group's choice, or Only me when none is chosen, which the presenter never leaves), `followersNote(group)`, `isNowHidden(group)`, `signInAgainNotice` (the shared notice, so the Compose form now shows one notice rather than a failure and a sign-in line together), `birthDay` (a `LocalDate?` from the text, decision 7), `weightLabel`, `heightLabel` and `heightInFeetAndInches`.
- `EditProfileUi` asks all of it. The samples gain the visibilities the presenter always fills (decision 9), so the shared screenshots change only where a choice read blank before.

### 5. The photo: `PhotosPicker`, compressed in Kotlin to a small JPEG

The same pick as the gear form, with the avatar's own compression: Swift loads the item's `Data` (`preferredItemEncoding: .compatible`), awaits a new `IosApp.compressAvatar(image:)`, which runs `compressAvatar` at `EditProfileLimits`' size and quality and returns the JPEG's `ByteString` or nil, and sends `AvatarPicked(jpeg:)`. `PhotoPick` becomes generic over what its compression returns (`PhotoPick<PickedPhoto>.gear`, `PhotoPick<KotlinByteString>.avatar`); its sequence and JPEG retry are unchanged and stay tested. `compressAvatar` moves from `:feature-profile-ui` to `:app-common-ui`, beside `compressPhoto`, since the shell may reach a feature only through its `-api` (`checkModuleBoundaries`); `:iosShell` takes `EditProfileLimits` from `:feature-profile-api`. The row: a 72-point circle (`Avatar` for a saved URL or the initial, the picked bytes through `UIImage(data:)` in the same circle), Change photo as the picker's label, and Remove photo when there is one.

### 6. The country: a searchable sheet, Kotlin's order and match

The Country row shows the chosen country's name (or Not set) and opens a sheet with its own `NavigationStack`: "Country", Cancel in the bar, a `List` with `.searchable`, Not set first and then the countries, the chosen one checked; a tap sends `CountryChanged` and closes. The codes and the rule move to `:feature-profile-api`, the one feature that offers countries (`CountrySearch`: `codes`, `sorted(names)`, `matches(sorted, key)`, over `CountryName(code, name, key)`); each UI makes the names with its platform (`countryName` in Compose, `Locale.current.localizedString(forRegionCode:)` in Swift, which on iOS both are NSLocale's) and folds the key with its platform's diacritic-insensitive folding and lowercase (`stripDiacritics` stays the Compose side's expect). The Compose field asks the same two functions.

Rejected: a `Menu` of 250 countries (no search); SwiftUI's own `localizedStandardContains` (a typed code wouldn't come first, so the two UIs would list differently).

### 7. The birthdate: the gear form's popover, from the text

The row shows the stored text, as the shared field does, with the gear form's buttons ("Choose birthdate", "Clear"), and opens a graphical calendar of days up to today in a popover. Kotlin turns the text into a `LocalDate` (`CalendarDate.toLocalDate`, made public, `YYYY-MM-DD` only, so malformed text is no date) for `state.birthDay`, and `BirthDateChanged` gains a constructor taking a `LocalDate?` (its ISO text, empty for nil), so Swift never formats or parses the text. `CalendarDay` converts as for the gear form.

### 8. Gender and the self-description

An `AppPickerRow` over `GenderChoices`' tokens, labelled by `GenderChoices.label`; the view holds `@State choseSelfDescribe`. Choosing sends `GenderChanged(valueFor(choice, gender))` and sets the flag; when `selfDescribes`, a `.row` field "How you describe your gender" for `state.gender` follows. The model's text is read in the body so the chosen value redraws on typing (the zones finding).

### 9. Registry, aliases, samples, snapshots

- `EditProfileScreen`, `State`, `Event`, `AvatarImage`, `EditProfileTexts`, `EditProfileLimits`, `GenderChoices`, `DetailGroup`, `CountrySearch`, `CountryName`, aliased in one block of `KotlinTypes.swift` headed for edit profile; the registration in one commented block after the upload queue's in `ScreenRegistry.app` (the zones session adds its block after the same line, so the merge is two adjacent blocks).
- `EditProfileSamples.all` gains the visibilities (Location Public, Birthdate Only me, Gender Followers, Body Mutuals) the presenter would fill; `variants` beside it: `imperial` (feet and inches, pounds, a self-described gender), `savedPhoto`, `pickedPhoto`, `nowHidden` (Location now Followers), `approving` (approves followers, Followers chosen, so no note), `saving`, `saveFailed`, `needsSignIn`. `ContentLoadingConformanceTest` keys stay.
- `#Preview`s with `SamplePages`, and `EditProfileSnapshotTests` over every sample and variant, light and dark, at a height the whole form fits (at most 2,730 points, the zones finding), plus the country sheet. `EditProfileTextTests` hold the words and conditions over the samples; `EditProfileLogicTests` the gender choice, the country search through Swift's folding and `PhotoPick`'s avatar shape.

## Risks / Trade-offs

- [The samples gain visibilities, so the shared screenshots change] → only the four pickers' shown values; the references are updated and looked at, and every other screen's are unchanged.
- [Swift counts what the athlete sees, Kotlin UTF-16] → as in the gear form, the native refusal can let an emoji-heavy name past Kotlin's count; the counter and `canSave` are Kotlin's, so it shows red and can't be saved, never saved too long.
- [`PhotosPicker` can't be driven by snapshots or tests] → `PhotoPick` is tested with its load and compress injected, plus the real `compressAvatar` over a JPEG; the picker itself is seen in the walk.
- [A walk that saves changes alice's profile on the local stack] → note each value first, put each back after, save only on alice.test.
- [Other sessions share the signed-in simulators] → build and test on my own clones; walk only when no other session's walk is in progress (their notes), or with the user's word.
- [iOS 18 can't be walked signed in] → the Swift tests run there with snapshots skipped; recorded under "Not verified".
