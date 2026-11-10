# Tasks

## 1. The rule

- [x] 1.1 `WindowWidth.of(width, height)` with `EXPANDED_MIN_HEIGHT` (500dp), and `WithWindowWidth` passing `maxHeight` (design decisions 1 to 4). Verify `WindowWidthTest`: the three widths at a tall height, a wide window at 499dp against 500dp, a phone on its side, a tablet on its side, an unbounded height, and a compact width staying compact when short.
- [x] 1.2 `SettingsListDetailDecoration` passes its height. Verify a `SettingsListDetailDecorationTest` case: a wide short window shows one page at a time, and the existing cases pass.

## 2. The screens

- [x] 2.1 A UI test that the activity page in a window of expanded width and 400dp of height shows its medium layout, not the side pane, and the wide one at 700dp of height (`AppPreviewUiTest`, in `:composeApp`, where the activity page's width test already is). It passes; it was not run against the old rule to see it fail.
- [x] 2.2 The import preview: no test added. Its tests don't tell the two layouts apart, and it reads the same `WindowWidth` the activity page's test covers.

## 3. Docs and the whole change

- [x] 3.1 `.agents/rules/adaptive-ui.md`: the height in the `WindowWidth` paragraph and the keyboard caution. The roadmap's open item on a phone on its side. Verify `scripts/check-agent-sync.sh`.
- [x] 3.2 `./gradlew format` and `./gradlew check`. One screenshot reference changed, as the rule intends: the activity page at 900 by 400dp (a phone on its side), which was the side pane's map alone and is now the medium layout opening on the hero. Rewritten and looked at; `check` passes after it.
- [x] 3.3 On the signed-in iPhone 17 Pro simulator on its side (874 by 402 points), an activity. Seen: the hero, the combine card and the stats across the readable width, with no side pane (before: a 244-point column beside an empty pane). The same activity on the iPad Air 11-inch on its side (1,180 by 820) keeps its side pane.

## Not verified

- **An Android phone on its side, and a short desktop or web window.** The rule is one function on every platform and is tested on the JVM; neither was run.
- **The import preview and Settings on Android on a short wide window** were not looked at; Settings' decoration has a test, the import preview none (2.2).
- **A map in the medium layout on a phone on its side:** the activity looked at has no map.
- **2.1's test failing without the rule** was not seen.
