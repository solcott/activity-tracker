# Tasks

## 1. Credit in the presenter

- [ ] 1.1 Add `SegmentCredit` (`OpenStreetMap(license)`, `Agency(license)`, `License(id)`) and `SegmentState.credit` to `app/screens/.../segment/SegmentScreen.kt`, with KDoc saying every screen that draws a segment's line shows it; verify `./gradlew :app-screens:compileKotlinJvm composeStabilityCheck` passes
- [ ] 1.2 Derive the credit in `SegmentPresenter` from `record.source` and `record.license` (null for `athlete`, `License` only for a non-blank licence of an unknown source), and add `SegmentPresenterTest` cases for `athlete`, `osm`, `agency`, an unknown source and a blank licence; verify `./gradlew :app-presenters:jvmTest --tests "*.SegmentPresenterTest"` passes

## 2. Credit on the segment screen

- [ ] 2.1 Show the credit in `SegmentUi` under "By @handle": the OSM text with the licence, opening `https://www.openstreetmap.org/copyright` through `LocalUriHandler`; the agency credit; the bare licence; add the strings to `composeResources/values/strings.xml`; verify `./gradlew :composeApp:compileKotlinJvm` and detekt (`UseAdaptiveComponents`) pass

## 3. Seed script

- [ ] 3.1 Add `--source osm|agency` to `docker/scripts/seed-segment.sh` (licence `ODbL-1.0` or `CC0-1.0`, default unchanged) and its usage line; verify by seeding an `osm` segment for `alice` on the local stack and checking `feed.getSegment` returns `source: osm`
- [ ] 3.2 Open the seeded `osm` and `agency` segments in the desktop app (`:desktopApp:hotRun`, hot-reload MCP screenshot) and verify each credit shows and the OSM link opens the copyright page

## 4. Integration

- [ ] 4.1 Run `./gradlew check` and verify it passes
