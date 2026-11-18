---
name: reuse-audit
description: Audit the codebase (or named modules) for duplicated code, code that belongs in a shared module, and hand-rolled code an established library could replace, across the Kotlin modules and the iOS app's Swift. Runs PMD CPD (`cpdCheck`, Kotlin and Swift) first, then reads past it for near-duplicates. Report-only; it never edits code. Use when the user asks about duplicate code, reuse, refactoring candidates, or "is there a library for this".
argument-hint: [module-path…] [--min-tokens N]
---

# Reuse audit

Arguments: `$ARGUMENTS`. With no module paths, audit the whole repo. A narrower scope is a list of directories (`feature/segment`, `server/appview`, `iosApp`). This skill reports; it never edits code. The fixes are follow-up work the user picks from the report.

## 1. Run CPD

Run `./gradlew cpdCheck -Pcpd.minTokens=60` through the `gradle-runner` agent (default 100; 60 catches the small UI and presenter repeats). It runs `cpdKotlin` and `cpdSwift`. Read `build/reports/cpd/cpd.txt` (Kotlin) and `build/reports/cpd/cpd-swift.txt` (Swift). Group the clusters by area (client, server, lexicon, iOS) and drop the ones outside the scope. CPD covers main source sets only and leaves out tests on purpose (`iosApp/iosAppTests` too), and it skips the Swift that `generateIosResources` writes (`iosApp/iosApp/Design/Generated`).

## 2. Read past CPD, in parallel

Launch these agents in one message, leaving out any whose area is outside the scope. Hand each one the path to its CPD report and its clusters.

- **Client** (`Explore`, very thorough): `core/*`, `feature/*`, `app/common/*`, `ui/*`, the app shells. It confirms or rejects each cluster, then looks for near-duplicates CPD can't see: the same list-screen scaffold or people row, the same load/refresh/paging state machine, repeated formatting or error mapping. It also flags code that hand-rolls an existing shared helper (`rememberSingleFlight`, `uiErrorOf`, content-state helpers, `Format.kt`, `ui/adaptive`). A suggested home must respect the module rules in AGENTS.md: `:app-common-api`, `:app-common-presenter`, `:app-common-ui`, `:ui-designsystem` or `core/*`. A presenter and a UI never share a module, and features meet only through `-api`.
- **Server** (`Explore`, very thorough): `server/*`. Privacy-relevant findings come first: visibility or tier logic written inline instead of calling `visibleTo`, `blocked()`, `effectiveFollow()`, `SpaceAccess` or the narrowest-tier helpers. After those: repeated SQL fragments, route/param boilerplate, pagination, and indexer upsert/delete patterns. `.agents/rules/appview.md` applies.
- **iOS** (`Explore`, very thorough): `iosApp/iosApp` (not `Design/Generated`), with `.agents/rules/adaptive-ui.md` as its guide. It confirms or rejects each Swift cluster, then looks for:
  - Swift that re-derives what Kotlin owns: number, stat, label or error text that should come from `StatText`, `LabelText`, `ErrorTexts` or `AppText` (as `Text/Stats.swift` and `Text/AppStrings.swift` read them), and rules or state a presenter already computes. The home is the Kotlin contract (`:app-common-api`, or the feature's `-api` or `-presenter`), so Android, desktop, web and iOS share it, never more Swift.
  - Views that hand-roll what `iosApp/iosApp/Design` has: the `AppButton` family, `AppTextField`, `AppPickerRow`, `AppListRow`, `AppCard`, `AppProgress`, `ContentStateView`/`RefreshStatusView`, `ErrorMessage`, `PagedList`, `pullToRefresh`, `PageBar`/`pageTitle`, `RemoteImage`, `LineChartView`; and in `Screens/`, `ActivityCardView`, `RouteMapView`/`RouteSketch`, `SegmentsMapView`.
  - Near-duplicates across `Screens/`: the same form or sheet section, list-row layout, empty or error state, or toolbar menu. A shared piece drawing the app's types goes in `Screens/`; a generic one in `Design/`.
  - Interop repeated at the Swift export boundary (dates and `LocalDate`, `KotlinByteString`, Kotlin collections and enums, observing presenter state) that a helper in `Kotlin/` (`Observed`, `KotlinCollections`, `KotlinTypes`, `Following`) covers or should.
- **Libraries** (`general-purpose`, so it can load skills): hand-rolled general-purpose code (geo math, time, encodings, compression, retry, caches, CSV, and so on). For Kotlin it checks every candidate with the `kotlin-multiplatform-libraries-expert` skill and reports only verified coordinates, with target support confirmed. For Swift it first checks whether an Apple framework or a package the app already pulls (Nuke, FITSwiftSDK, libwebp; the Xcode project's package references) does it, then Swift Package Index or the package's repository, and reports only maintained, permissively licensed SPM packages that support iOS 18, with the version checked.

Each agent returns up to ~15 findings, each with a title, file:line, what repeats, the suggested home or library, effort (S/M/L), and whether the repetition is intentional.

## 3. Don't recommend

- An atproto SDK (Ozone etc.), or replacing the hand-written XRPC/OAuth/DPoP layer in `core/atproto`. That is a hard project rule.
- A client library without a wasmJs target, unless the code sits in a platform source set the library covers.
- Merging `expect`/`actual` pairs that differ on purpose (a platform's own API behind one signature), or lexicon mirrors that must match their JSON field for field.
- Merging shapes that look alike but carry different privacy rules. Two queries that differ only in which tier they admit are not duplicates.
- Moving logic from Kotlin into Swift, or sharing a Compose view with iOS: the iOS app ships no Compose UI (`checkIosDependencies`). Logic a SwiftUI view and a Compose UI both repeat moves down into the shared Kotlin contract or presenter.
- Editing generated Swift (`Design/Generated`, the String Catalog). Their sources are `strings.xml`, `AppIcon.kt` and the drawables, through `generateIosResources`.
- A Kotlin signature Swift export can't carry (a Compose type, `Pair`/`Triple`, a parameter named `self`, a type from a module `iosExport/build.gradle.kts` hides) to share code with Swift.
- A Swift wrapper for what stays the system's: sheets, menus, alerts, date and photo pickers, toggles, segmented pickers, `refreshable` (`adaptive-ui.md`).

Spot-check every finding you keep against the code. An agent's "duplicate" is a claim, not proof.

## 4. Report

Write `build/reports/reuse-audit.md`. It's gitignored, so nothing tracked changes. Use three sections: Duplicates, Shared-home candidates, Library replacements. Within each, rank findings by value against effort, tag each with its area (client, server, iOS), and keep each to a few lines: locations, what repeats, the proposed extraction or library, and effort. End with the CPD clusters rejected as false positives, Kotlin and Swift, one line each, so the next run doesn't chase them.

In chat, summarize the top items and offer them as follow-up work. Once the remaining CPD clusters are cleaned up, suggest lowering the threshold or wiring `cpdCheck -Pcpd.fail=true` into CI.
