# Tasks

## 1. UDDF writer

- [x] 1.1 Add `device: String? = null` to `ExportActivity` and verify `./gradlew :core-activityfile:jvmTest` still passes unchanged
- [x] 1.2 Write `UddfWriter.write(activity, zone, createdAt)` in `:core-activityfile/commonMain`: generator, dive computer, site from `dive.site` and the first positioned point, `gasdefinitions` (record gases then switch-only gases, ids from percents), one repetition group and dive with `informationbeforedive`, `tankdata`, waypoints (one per second with a depth, first point of the second) and `informationafterdive`, in UDDF 3.2 element order, SI units, `escapeXml`/`toXmlDecimal`; verify it compiles with `./gradlew :core-activityfile:compileKotlinJvm`
- [x] 1.3 Add `UddfWriterTest` (commonTest) round-tripping through `ActivityFileReader` in a fixed zone: a deco dive with switches 21/0 → 50/0 at 1920 s, two tanks with pressure series, deco stop/NDL/ppO₂/CNS streams, a site and entry/exit track, and a device comes back with the same start, summary, site name, gases, switches, tanks and streams; verify `./gradlew :core-activityfile:jvmTest --tests "*.UddfWriterTest"` passes
- [x] 1.4 Cover the edge cases in `UddfWriterTest`: an older dive with two gases and no switches switches only to the first at the start; a merged dive whose switch names a gas the record lacks defines it; a closed-circuit dive writes `divemode closedcircuit`; no site and no track writes no `divesite`; points without depth and two track points in one second give no extra waypoints; local time is written in the given zone with no offset; XML special characters in site and device are escaped; verify the test class passes
- [x] 1.5 Add a jvmTest that checks the element order of each parent the writer emits against UDDF 3.2's sequence (a small expected-order table per parent, not a schema download) and verify it passes

## 2. Offering and exporting UDDF

- [x] 2.1 Add `ExportFormat.UDDF("uddf", "application/xml")` last, give `ActivityExporter.formats` an `isDive` parameter that adds UDDF exactly when TCX is offered on a dive, pass `record.device` and build UDDF bytes with `UddfWriter` in `build`; extend `ActivityExporterTest` for formats (dive with and without track, run never offered UDDF), a UDDF export's name ending `.uddf` with `application/xml`, and a non-dive asked for UDDF throwing `ExportUnreadableException`; verify `./gradlew :core-data:jvmTest --tests "*.ActivityExporterTest"` passes
- [x] 2.2 Pass `record.sport == Sport.DIVE` from `ActivityDetailPresenter` through `exportFormats` (and into its `remember` key), and extend `ActivityDetailExportTest` so an own dive offers GPX, TCX, FIT, UDDF in that order and an own run doesn't offer UDDF; verify `./gradlew :app-presenters:jvmTest --tests "*.ActivityDetailExportTest"` passes
- [x] 2.3 Add `export_format_uddf` ("UDDF") to `strings.xml` and its case in `ExportActivityUi`'s label `when`; verify with `./gradlew :composeApp:compileKotlinJvm` and, in the desktop app (`hot-reload.md`), that Export on an own dive lists UDDF
- [x] 2.4 Update the Export paragraph of `.agents/rules/activity-files.md` with `UddfWriter` (layout, zone, gases without switches, waypoint per second) and verify `scripts/check-agent-sync.sh` passes

## 3. Integration

- [x] 3.1 Run `./gradlew check` (via the gradle-runner agent) and verify it passes, including ktfmt, detekt and `composeStabilityCheck` (passed apart from a flaky, unrelated `FeedPresenterTest.theFeedLoadsWhileTheRouteReTrimIsStillRunning`, which passed 3/3 when re-run alone)
- [x] 3.2 Manually export one deco dive with switches and tanks and one older dive without switches from the desktop app, import both into Subsurface, and verify each shows the expected start time, depth profile, gases and switches (and tanks for the first); record the result in this task
  - Result (2026-10-16, Subsurface on macOS, checked from its saved `.ssrf`). No stored dive had switches, deco and tanks together, and none lacked stored switches, so two dives were used, and the older-dive case rests on `UddfWriterTest`:
    - **Evening CCR Dive** (17/30 → 52/0 at 59:06, deco, no stored tanks): start 2023-10-20 17:13:38 local ✓, 62.02 m and 6275 samples to 105:45 ✓, deco stops ✓, entry coordinates ✓. The first export came in with no cylinders, which led to the gas-only tanks (see design). Re-exported, it has both cylinders ✓, but both switches land on 17/30 and the dive shows as OC ✗: Subsurface's `uddf.xslt` can't resolve `switchmix` or `divemode` in a namespaced file (confirmed by running it with `xsltproc`; without the namespace it's correct). Accepted: the file keeps the UDDF namespace.
    - **Morning Single-Gas Dive** (air, one tank): start 2023-11-22 07:44:47 local ✓, 17.65 m and 2907 samples ✓, air cylinder and switch at 0:00 ✓, 223.6 → 65.3 bar with 110 of 112 readings ✓.

## Workflow follow-up

- Archive the change with `/opsx:archive` once 3.2 is done, syncing the `feed/activity-export` delta into the main spec.
