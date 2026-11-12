# Tasks

## 1. Kotlin (no visible change but the corrected sample)

- [x] 1.1 `RouteMap.of(highlight:)`, `RouteMap.highlight` and `RouteShape.highlight`, kept out of `key`. Verify `RouteMapTest` covers the highlight, its sketch and the unchanged key.
- [x] 1.2 `CreateSegmentTexts` and `CreateSegmentState.routeMap`; the Compose form resolves them. Verify a texts test (names resolve, the lines for each check) and the create-segment UI and screenshots.
- [x] 1.3 The loaded sample's stretch (480 m to 1,440 m, highlighted) and `CreateSegmentSamples.variants`. Verify `ContentLoadingConformanceTest`, and re-recorded Android references looked at (only `CreateSegmentScreenshots`' changed).

## 2. Native

- [x] 2.1 The route map's highlight, live and in the sketch. Verify a snapshot of the form's map shows it.
- [x] 2.2 `RangeSlider` in `Design/`, with its own preview (the design gallery is left to the sibling session's edits). Verify a snapshot of its states.
- [x] 2.3 `CreateSegmentView` with aliases, samples, `#Preview`s, the registry entry and `ScreenRegistryTests`. Verify `CreateSegmentSnapshotTests` on iOS 26.0, each image looked at.
- [x] 2.4 Swift Testing texts tests over `CreateSegmentTexts` (`CreateSegmentTextTests`). Verify they pass.

## 3. The whole change together

- [x] 3.1 `./gradlew format` and `./gradlew check -Pkotlin.incremental=false`. Verify both pass.
- [x] 3.2 `xcodebuild test` on an iOS 26.0 clone and an iOS 18.4 clone (built for 18.4). Verify both pass (snapshots skipped on 18.4); delete the clones.
- [x] 3.3 The Release configuration for the simulator, ad-hoc signed. Verify it builds and passes the stub check.

## 4. See it

- [x] 4.1 On a signed-in simulator on the local stack, if one is at hand: open Create segment from an own activity with a route, drag the stretch, type a name; publish only if it can be deleted again. Verify by noting what was seen; record what couldn't be seen under "Not verified".

## 5. Docs

- [x] 5.1 The roadmap: a Phases bullet, "Phase 4 findings (create segment)", and the activity page's open item updated. Verify `openspec validate ios-native-create-segment --strict` passes.
- [x] 5.2 `.agents/rules/compose-screens.md` where a rule changed. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [x] 5.3 A "Not verified" section in this file listing what wasn't seen and why.

## Walk (iPad Air 11-inch (M3) `3F116204`, iOS 26.0, signed in as alice on the local stack; this change's Debug build)

- **Create segment (4.1).** You > Afternoon Mountain Bike Ride (Nov 3) > the menu > Create segment opened the native form: the
  live MapKit map framed on the whole ride with the default stretch (the whole route, 22.58 mi · Ride) highlighted over it, the
  range slider with both thumbs at the ends, the Name row, and the public note saying it will link to this activity (a Public ride
  with a Public map). Publish was disabled.
- Dragging the end thumb left and the start thumb right moved the highlight along the route and the line under the slider
  (8.25 mi, then 6.66 mi · Ride); the map kept its framing throughout. A vertical drag elsewhere wasn't tried on the short iPad form.
- Typing "Walk test" in Name enabled Publish. Back left the form; nothing was published, so nothing needed putting back.
- **Seen in passing:** a menu item tapped while the owner's menu was still opening landed on Edit (the first item); Cancel left it
  unchanged. The first text typed into the newly focused Name field was dropped while focus arrived; the second landed. Both are
  the simulator's timing, not this form's.
- The iPad is left on the ride's page running this change's Debug build. The iOS 26.0 and 18.4 clones (`B5D15E0A`, `FBE6BDEB`) are
  deleted.

## Not verified

- **Publishing on a device:** publishing writes a public `feed.segment` to alice's repo on the shared local stack, which other
  sessions' walks read, so the duplicate rows, the allowance and a failed check were seen only in snapshots, as were publishing's
  progress in the bar and the return to the activity.
- **VoiceOver on the slider:** each thumb's name, its position read as "… along the route", and stepping by swipes were not tried
  with VoiceOver running; the accessibility modifiers are in place and the words come from `CreateSegmentTexts` (tested).
- **An activity no segment can be made from, iPhone widths and dark mode on a device:** snapshots cover the three reasons, light and
  dark, at an iPhone's width.

## Checks

- `./gradlew check -Pkotlin.incremental=false`: passed.
- `xcodebuild test` on an iOS 26.0 clone: 420 tests passed, the new snapshots compared after recording and being looked at.
- `xcodebuild test` on an iOS 18.4 clone, built for it: 420 tests passed, snapshots skipped.
- Release for the simulator, ad-hoc signed: built, the stub check passed.
