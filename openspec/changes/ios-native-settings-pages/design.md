# Design

## Context

What exists, from the code:

- **The contracts** (`feature/settings/api`). `ActivityDefaultsState` holds `defaults: VisibilityFields?` (null while loading), `saving`, `error` and a sink whose only event is `Back`; the pickers send `VisibilityEvent`s to `defaults.eventSink`. `VisibilityFields` (`:app-common-api`) holds the activity's visibility, `categories: ImmutableList<CategoryVisibility>` (a token and a visibility, both strings), `canChoose`, `spacesUnsupported`, `approvesFollowers`, `runningForm`, and `categoryOptions: ImmutableList<String>`. `SegmentsAndGroupsState` holds nullable booleans for matching, leaderboards and bracket boards, `groupActivities: GroupChoice?` (an enum of `:core-data`: `NEVER_ASKED`, `OFF`, `CONNECTED`, `ANYONE`), three `canUse…` flags, four `saving…` flags, `error`, and events for each choice.
- **The shared UIs** (`feature/settings/ui`, `:app-common-ui`) build text with rules in composables: the page notes (`saving` over locked over help on Activity defaults; sign-in-again over saving over help for each of segments and groups), `lockedVisibilityNote` (spaces unsupported or sign in again), a category's name (Cadence alone when there is no running form; an unknown token as it is) and help (four categories have one), and the Followers note under any picker set to Followers when the athlete doesn't approve followers. They also decide when each switch is offered and enabled, and that a grouping never asked shows as Off, from the list `GROUP_CHOICES`.
- **The iOS shell** draws a pushed native screen with the stack's bar (`SettingsView` is the model). Beside the list, `SettingsColumns` draws the split's page with `ScreenView(inPane: true)`, which today only tells a fallback page to leave the stack's bar alone; a fallback page draws its own Compose bar in the column, with no back button as the pane's first page (`HostRecord.paneRoot`).
- **Samples**: `ActivityDefaultsSamples` (loaded, loading, locked, saveFailed) and `SegmentsAndGroupsSamples` (loaded, loading, signInAgain, saveFailed) in `:app-fixtures`.

Neither page needs infrastructure beyond a form: no loaded content, no map, no chart, no text field. The scope stays the two pages asked for.

## Goals / Non-Goals

**Goals:**

- Two native settings pages that say and offer exactly what the shared pages do, alone and beside the list.
- One answer, written into the design system, for a native page's bar in the detail column, which every later settings page uses.
- Every rule these pages follow (text, conditions, the grouping choices) written once in Kotlin, asked by both UIs.

**Non-Goals:**

- Native visibility pickers for other screens (an activity's edit, record, import): they keep the shared UI.
- A back button in the column header: no native page is deeper than the pane's first yet.
- Any other settings page.

## Decisions

### 1. Probe first: the bar in the detail column

Asked with throwaway code (a scratch root view standing in for `RootView` by launch argument, a tab whose stack pushes the two-column destination, `SettingsColumns` itself, a form page) on an iPad Pro 13-inch with iOS 26.0 and one with iOS 18.4, read from screenshots. Results, recorded in the roadmap under "Phase 4 probes (settings pages)":

- **A page's `.navigationTitle` in the column never shows.** The bar over both columns keeps the list's "Settings"; the page's title is lost. So a native page in the column needs a header of its own, as the fallback pages already draw.
- **A header in the column's top safe-area inset works on both versions.** The column's safe area ends under the outer bar, so a header placed with `.safeAreaInset(edge: .top)` sits directly beneath it, centred over the column, in line with the list's first section. With a bar material and a hairline it reads as a bar on 18.4; on 26.0 `.safeAreaBar(edge: .top)` places the same header with the system's scroll edge effect and no fill, as the system's own bars look at rest.
- **`NavigationSplitView` as the destination, with its column visibility held at `.all` and the sidebar button removed, now draws two columns with a bar each** on both versions (the earlier probe let it choose, and on 18.4 it hid the sidebar). It still isn't taken: the outer bar loses its title to the sidebar's (a large title on 18.4, inline on 26.0, a floating panel on 26.0), the list would no longer be the same view narrow and wide (so crossing the size would rebuild it, which ui/settings-layout's resizing requirement forbids), and every fallback page would draw its Compose bar under the split's bar. Kotlin would also have to fight its collapsing when the window narrows.

The content of the probe pages fit an iPad's height, so scrolling under the header was not seen in the probe; it is checked in the walk (task 6).

### 2. `pageTitle(_:)`: the stack's bar alone, a column header beside the list

The design system gains one modifier, `pageTitle(_ title: Text)`, which a native page uses in place of `.navigationTitle`:

- **Alone** (the page is its stack's destination): `.navigationTitle(title)` with the inline display mode, as `SettingsView` does. The back button is the stack's.
- **In a column** (`@Environment(\.inPane)` true): the column header `PageBar`, the title in `.headline`, centred, at least 44 points tall: `.safeAreaBar(edge: .top)` on iOS 26, and `.safeAreaInset(edge: .top, spacing: 0)` over the `.bar` material with a hairline before it. It sets no navigation title, and has no back button: the page is the pane's first.

`ScreenView(inPane:)` sets `\.inPane` for a native screen too, beside its existing meaning for a fallback. A deeper native page in the column would add a back button to `PageBar` from `HostRecord.paneRoot`; none exists yet, so that is left for the first.

### 3. Text and conditions chosen in Kotlin

In `:app-common-api`, beside `VisibilityFields`:

- `LabelText.detailCategory(category)`: `detail_category_<token>` for a token of `DetailCategory.all`, else the token as written (a closed `when` over known tokens, not a lookup with a fallback, so a token that happens to match another string's suffix can't pick it up). `detailCategoryLabel` in `:app-common-ui` resolves it, which `CategoryLock` also uses.
- `VisibilityTexts.categoryName(fields, category)`: Cadence alone without running form, else `LabelText.detailCategory`; `categoryHelp(category)`: the four helps, else null; `followersNote(selected, approvesFollowers)`: the note or null; `lockedNote(fields)`: unsupported or sign in again. `lockedVisibilityNote` goes; `ActivityVisibilityForm`, `VisibilityPicker` and `DetailVisibilityPicker` resolve these.
- `VisibilityFields.activityOptions`: what the activity's picker offers (all four), next to `categoryOptions`, so neither UI holds the list.

In `:feature-settings-api`:

- `SettingsTexts.defaultsNote(defaults, saving)`, `segmentsNote(state)`, `groupsNote(state)`, the shared pages' `when`s moved as they are.
- `ActivityDefaultsState.canChange`: loaded, choosable and not saving.
- `SegmentsAndGroupsState`: `canChangeMatching`, `offersLeaderboards`, `canChangeLeaderboards`, `offersBracketBoards`, `canChangeBracketBoards`, `canChangeGroups`, `shownGroupChoice` (`NEVER_ASKED` and unknown as `OFF`) and `groupOptions` (decision 4), each the shared page's expression as it is.

`SegmentsAndGroupsUi` and `ActivityDefaultsUi` ask them. Getters on a state are not fields, so the strict stability check is unaffected. `SharedTextNamesTest` gains the category names. No Android screenshot reference may change.

### 4. A list of enum values reaches Swift as objects

The probes of `ios-native-settings` showed a Kotlin enum converts only as a property's own value: a `List` of an enum stops the app in the generated getter, and an immutable collection of one hands Swift opaque elements. So the grouping choices are `groupOptions: ImmutableList<GroupOption>`, where `GroupOption(choice: GroupChoice, label: AppText)` is an `@Immutable` class of `:feature-settings-api`. Swift reads the list with `elements(as: GroupOption.self)` and each `choice` as the Swift `GroupChoice`, which names the enum's type in an exported signature too. This is the general rule recorded for the open item: wrap each value in a contract object. A Swift test reads the three options and their values.

The other open item, a `:core-data` or `:core-lexicon` class that appears only as a collection's element, doesn't arise: these pages' collections hold strings and `CategoryVisibility` (a contract class). It stays open.

### 5. `ActivityDefaultsView`

A `Form` over `ActivityDefaultsState`, `pageTitle(.settingsActivityDefaults)`:

- **New activities** (header `settings_defaults_activity`): a choice labelled `visibility_label`, over `activityOptions`, labelled by `LabelText.visibility`, sending `ActivityChanged`; its footer is the Followers note for the activity's choice.
- **Each detail** (header `detail_visibility_heading`, the help `detail_visibility_help` as its first footer line): a choice per category, its label the category's name with its help under it, over `categoryOptions`, sending `CategoryChanged`.
- **Every choice's row** keeps the choice on one line at the trailing edge, with a long label wrapping beside it: the form's own `Picker` row moves the choice under a label too long for one line (the user caught it on Physiology in the first walk). Each row is an `HStack` of the label and a `Menu` holding an inline `Picker`, labelled with the current choice and the system's up-down chevron, which opens the same checked menu and keeps the form's row height (a menu picker beside the label made rows 12 points taller; `LabeledContent` wraps like the form's row). VoiceOver reads the menu by the row's name and value; the visible label is hidden from it. Shown only when `categories` isn't empty. Its footer adds the Followers note once when any category is set to Followers without approval: the shared page repeats it under every such picker, which in a grouped form would be up to ten copies of one sentence. The words are the same; only where it sits differs.
- **The note** (`SettingsTexts.defaultsNote`) and `ErrorMessage` for `error`, as the last section's footer, as the shared page ends with them.
- Every picker is disabled unless `canChange`. While `defaults` is null the form is empty but for an error, as the shared page is.

### 6. `SegmentsAndGroupsView`

A `Form`, `pageTitle(.settingsSegmentsAndGroups)`:

- **Segments** (header `settings_segments_heading`, footer `segmentsNote`): a `Toggle` for matching, then, when offered, the leaderboard toggle and the bracket-board toggle with its help under its title, each disabled unless its `canChange…`.
- **Group activities** (header `settings_groups_heading`, footer `groupsNote`): a segmented `Picker` over `groupOptions`, selected `shownGroupChoice`, sending `GroupActivitiesChanged`, disabled unless `canChangeGroups`.
- `ErrorMessage` for `error` in a section of its own at the end.

### 7. Registry, types, samples, previews and snapshots

- `ScreenRegistry.app` registers `ActivityDefaultsScreen` and `SegmentsAndGroupsScreen`; `KotlinTypes.swift` aliases their states and `GroupOption`; `Samples` reads both pages' samples.
- Previews: `SamplePages` over each page's samples, and each page beside the list (`SettingsColumns` with `\.inPane`).
- `SettingsPagesSnapshotTests`: every sample of both pages alone at the phone size, light and dark, with `everySampleHasASnapshot`; both pages, loaded, beside the list at 1,032 by 800 points with their rows marked. `DesignGallery` gains the column header, and the design snapshots its reference.
- The `theListBesideAPage` reference of `SettingsSnapshotTests` stays, its stand-in unchanged.

### 8. Walking the pages

On the signed-in iPhone 17 Pro (iOS 26.0): both pages alone, every choice changed and changed back, back by the button and the swipe. On the signed-in iPad Air 11-inch (iOS 26.0), upright and on its side: Activity defaults beside the list on opening, Segments & groups chosen, scrolling Activity defaults under its header, a fallback page (Privacy zones) chosen after and back. On an iOS 18.4 simulator the build is launched to sign-in only: its simulators can't reach the local stack (roadmap), so the pages there rest on the probe and the Swift tests, run on 18.4.

## Risks / Trade-offs

- **[The Followers note sits once per section natively, under each picker in the shared page]** → Same words and the same condition from Kotlin; placement is the view's. Recorded in the roadmap's findings.
- **[`safeAreaBar` is iOS 26 only]** → The pre-26 header uses an inset with the bar material, as the probe drew it on 18.4.
- **[Getters added to states are read by Swift through generated bridges]** → The Swift tests read every one on the samples; the stub check covers them in the iOS build.
- **[iOS 18 not walked signed in]** → Listed as not verified; the Swift tests run on an 18.4 simulator.
