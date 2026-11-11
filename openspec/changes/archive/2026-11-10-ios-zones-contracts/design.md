# Design

## Context

What exists, from the code (see proposal.md for why):

- **Zone kinds.** `SportZones.kinds(sport)` (`:core-data`) returns a `Set<ZoneKind>` (`HeartRate`, `Power`, `Speed`) in that order. `TrainingZonesPresenter` and `SportZonesPresenter` store it as `kinds: ImmutableSet<ZoneKind>` on `ZoneSummary` and `SportZonesState`, and `TrainingZonesUi` and `SportZonesUi` ask only `ZoneKind.X in kinds` (eight places). The samples (`TrainingZonesSamples`, `SportZonesSamples`) hold heart rate and power, and heart rate and speed. The presenter tests compare the set.
- **Element types.** `ZoneSummary`, `ZonesState` and `SportZonesState` hold `ImmutableList<Zone>?` (`:core-data`, `Zone(label, name: ZoneName, low, high?)`), and `PrivacyZonesState.zones` is `ImmutableList<PrivacyZone>?` (`:core-lexicon`). Those modules reach Swift only as far as the contracts' signatures name their types, and a collection's type argument is erased, so nothing names either class. `ZonesUi`'s `ZoneRow` labels a zone `stringResource(zones_label, zone.label)` ("Zone 2"), and `PrivacyZonesUi`'s `ZoneRow` names one `zone.label ?: stringResource(privacy_zones_unnamed, index + 1)`.
- **Shown rows.** `ThresholdHistorySection.shownRows: List<ThresholdHistoryRow>` is `if (showingAll) rows else rows.take(3)`; `take` already copies, the `showingAll` branch hands back the persistent list. `ThresholdHistoryUi` iterates it.
- **What earlier probes found** (roadmap, "Phase 4 probes (`ios-native-settings`)"): an enum element of a collection reaches Swift as an opaque `_KotlinExistential`, and a `Set` of enums stops the app in the generated getter (`GrantStatus.Outdated.missing`); `PrivacyZone` as an element is an opaque object until any exported signature names it, after which the same element casts; a `List` getter returning the persistent list stops the app (`-[_KotlinExistential count]`), and a copy reads as a typed array.

## Goals / Non-Goals

**Goals:**

- Every field and getter of the three zones contracts that a native page would read is readable from Swift without a crash or an opaque element, proven by Swift tests over the shared samples.
- The shared UIs show exactly what they do today; the Android screenshot references don't change.

**Non-Goals:**

- Native views, previews or snapshots for these pages.
- Changing what a contract holds for Swift's sake beyond the blockers: the zone lists and privacy zones stay `ImmutableList`s of their own classes (dev/ios-swift-interop: a contract doesn't change a collection's type for Swift).
- The rest of a zone row's text (its name by `ZoneName`, shared with the activity charts' time in zone, and its range with units and paces). That move belongs to the native page that first needs it.

## Decisions

### 1. Probe first

A throwaway Swift test on a fresh iOS 26.0 iPhone 17 simulator (not one of the signed-in ones), over `TrainingZonesSamples`, `SportZonesSamples`, `PrivacyZonesSamples` and `ThresholdHistorySamples`, printing what Swift gets for each kinds set, each zone list's elements, the privacy zones' elements, and `shownRows` collapsed and with every row shown. Results go under "Phase 4 probes (zones contracts)" in the roadmap. **Found:** every kind, zone and privacy zone read as an opaque object and the export had no Swift type for `ZoneKind`, `Zone` or `PrivacyZone`; `shownRows` with every row of a five-row section shown read as a typed array, and only an empty persistent list stopped the app; and Swift can't build a section to read (no `copy`, and a constructor taking an immutable list has no initialiser). The fixed contracts are then read by the same tests made into real ones (decision 5).

### 2. Zone kinds as option objects

`kinds` becomes `ImmutableList<ZoneKindOption>` on both `ZoneSummary` and `SportZonesState`, where `@Immutable data class ZoneKindOption(val kind: ZoneKind)` lives in `:feature-settings-api` beside the zones contracts, with `ZoneKindOption.forSport(sport: String?)` building the list from `SportZones.kinds` in its order (it takes the sport, not the set, so no signature Swift sees holds a set of enums). Both classes gain `fun offers(kind: ZoneKind): Boolean`, which the shared UIs ask instead of `in`, and a native view may ask too (an enum is a parameter Swift passes as its own type).

- *Why an object and not the enum list*: a `List`/`ImmutableList` of an enum can't reach Swift (the same failure as a set); the object holding the value as a property is the pattern `GroupOption` set, and the spec requirement for enum lists.
- *Why no label*: unlike the grouping choices, nothing shows a kind by name (each section has its own heading), so the object holds only the value. A label can join it when a page needs one.
- *Alternatives*: three booleans (`offersPower`, `offersSpeed`, …) read well but stop matching `SportZones.kinds` as kinds are added, and the native page would still want the list to lay out sections in order; text for each kind (as `AccountTexts.missingFeatures` did) fits members that are only shown, not branched on.
- *Kept on the state, not replaced*: the field keeps its name, so the samples and tests change only in type.

### 3. Naming `Zone` and `PrivacyZone` through text functions

`ZoneTexts` in `:feature-settings-api`: `label(zone: Zone): AppText` (`zones_label` with the zone's label as written) and `privacyZoneName(zone: PrivacyZone, index: Int): AppText` (the label as written, or `privacy_zones_unnamed` with `index + 1` formatted by `formatWhole`). `ZonesUi`'s and `PrivacyZonesUi`'s `ZoneRow`s resolve them with `.string()`.

- *Why these*: an exported signature naming the class is all Swift needs, and each of these is text a native row will need anyway (dev/shared-display-text: when a native view needs text a composable builds, the choice moves to an `AppText` function first). Nothing else in either contract changes.
- *Alternatives*: a typed accessor on each state (`zone(at:)`) or a cast helper in `:iosShell` would name the class too, but each is a declaration with no other use, kept alive only by a comment; a `List<Zone>` twin of each field is ruled out by the rule against `List` twins, and would add a field per list (three zone lists on two states, one on a third).
- *Why only these two*: the zone's name and range also follow rules, but the name's lookup (`zoneName`, `:app-common-ui`) is shared with the activity charts, and the range needs unit and pace text; moving them is the native page's work and changes nothing Swift can or can't read.

### 4. `shownRows` returns a copy

`if (showingAll) rows.toList() else rows.take(COLLAPSED_ROWS)`. **Found** (decision 1): a non-empty persistent list already reads as a typed array through this getter; only the empty one stops the app, and a threshold's section always has a row. The copy stays, since it costs nothing and holds whatever the list is, and the roadmap's open item is corrected to the empty case. It stays a `List`: it is a getter, not a stored field, so Compose stability doesn't see it, and the generated Swift getter then gives a typed array.

### 5. Swift tests

`ZonesContractsTests` (Swift Testing) in `iosApp/iosAppTests`, over the samples: the kinds of the default summary and the running override as `[ZoneKind]` through `elements(as: ZoneKindOption.self).map(\.kind)`, and `offers`; the default summary's heart rate zones through `elements(as: Zone.self)` (labels, names, bounds, the last open-ended); the privacy zones through `elements(as: PrivacyZone.self)`, with the label, radius and centre, and `ZoneTexts` resolved through `AppStrings`; `shownRows` collapsed and with every row shown (`ThresholdHistorySamples.variants["allRows"]`, decision 6). Type aliases for `ZoneKind`, `ZoneKindOption`, `Zone`, `PrivacyZone`, `ZoneTexts`, the three zones states and `SportZonesState` go in `KotlinTypes.swift`; `Samples` gains `sportZones`. `KotlinCollectionsTests`' privacy zones and threshold history reads stay as they are.

### 6. A sample with every row shown

`ThresholdHistorySamples.variants`, apart from `all` (whose keys are the content-loading conformance's and the Android screenshot set's), holds `allRows`: a loaded history whose one threshold has five values, every one shown. It is what the Swift test reads, since Swift can't make one.

## Risks / Trade-offs

- [The text functions name the types only while a native-facing function takes them] → The Swift tests read the elements typed, so dropping a function that was the only name fails a test, not a device.
- [`offers(kind)` is a function Swift reads as a method taking the enum] → The Swift test calls it, so a parameter the export can't take shows there.
- [The stub check could flag a newly named `:core-data` or `:core-lexicon` type] → `:iosExport:checkSwiftExportStubs` runs through the iOS build; a stub would be fixed before the change lands.
