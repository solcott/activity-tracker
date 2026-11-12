# Tasks

## 1. Kotlin rules (no visible change beyond grouped counts)

- [x] 1.1 `FeedTexts`, `FeedScopeOption`, `FeedAddChoice`, `FeedAddKind` and `ImportCardTexts` in `:feature-feed-api`; `FeedState.showsEmpty` and `keyOf` (decision 1). Verify `FeedTextsTest`.
- [x] 1.2 `FeedUi` and `ImportCard` ask them. Verify `:feature-feed-ui:jvmTest` and the Android screenshot tests, with only grouped counts changed (decision 2).
- [x] 1.3 `FeedSamples.variants`. Verify `ContentLoadingConformanceTest`.

## 2. The native views

- [x] 2.1 Type aliases (one appended block) and samples (appended). Verify `FeedTextTests` reads every sample it names.
- [x] 2.2 `FeedPage`, `FeedUploadRows`, `FeedImportCard`, `SegmentsPrompt`, `GroupsPrompt` and `pullToRefresh` (decisions 3 to 7). Verify their previews and `FeedSnapshotTests`, light and dark, each image looked at.
- [x] 2.3 The registry entry (decision 8). Verify `ScreenRegistryTests` names `FeedScreen` as native.

## 3. The whole change together

- [x] 3.1 `./gradlew format` and `./gradlew check`. Verify both pass.
- [x] 3.2 The Swift tests on clones of the iOS 26.0 and 18.4 snapshot simulators. Verify they pass, snapshots compared on 26.0 and skipped on 18.4.
- [x] 3.3 The Release configuration for the simulator. Verify it builds and passes the stub check.

## 4. See it

- [x] 4.1 On the signed-in iPad Air 11-inch `3F116204-14F9-4645-9E88-D02627E2ACC6`, as alice on the local stack: the scopes, the filters, a card opening its page, a post, pull to refresh, the end of the list; anything changed put back. Verify by noting what was seen; record what needs data the stack lacks under "Not verified".

## 5. Docs

- [x] 5.1 The roadmap: a Phases item 4 bullet, "Phase 4 findings (feed)", the open items (the feed's closed; paging, Report on feed posts, the bar's type sheet). Verify `openspec validate ios-native-feed --strict` passes.
- [x] 5.2 `.agents/rules/compose-screens.md` and `adaptive-ui.md` where a rule changed. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [x] 5.3 A "Not verified" section listing what wasn't seen and why.

## Walk (iPad Air 11-inch `3F116204-14F9-4645-9E88-D02627E2ACC6`, iOS 26.0, signed in as alice on the local stack; this change's Debug build)

- **4.1.** The Feed tab opened on the native page: the large title "Following", the scope, types and date chips on one line, and
  alice's and bob's activities as native cards (tier badges, the owner's Gear lock, a route sketch, bob's blurred photos, Like on bob's).
  The large title offered no menu (a tap did nothing), which is why the scope chip exists (design decision 4); scrolled, the inline
  title showed its chevron and opened Following and Mine. The scope chip's Mine turned the title and chip to "Mine" and listed her own
  activities by start; Following put it back. Last 7 days filled the date chip, showed Clear filters and dropped the November 3
  activity; Swim from the type sheet then left nothing, and the empty feed said "No activities match these filters." with Clear
  filters, which emptied both chips and brought the list back, the scope kept. A pull down showed the system's spinner, which closed
  once the refresh ended. Add listed Record, Add manual activity and Import activity file with their icons. A card opened its
  activity (the shared page) and back returned to the feed where it was. Flinging down reached the end of the list, the oldest ski
  days, with nothing more to load.
- **Put back:** nothing was written; the scope and filters are session state and were left at Following with no filters.
- **iOS 18.4:** the Swift tests ran on a clone of `958C0DC3-FF20-44F5-9D02-7A170A295DD3` (snapshots skipped); not walked signed in,
  which iOS 18 can't be (roadmap open items).

## Not verified

- Posts, reposts and quotes in the feed, import cards, the upload rows, the segments and grouping prompts, a failed action and a
  failed first load on a device: alice's feed on the local stack has none of them (no posts, no import batch, nothing uploading, both
  prompts answered). The snapshots draw each.
- Report on a feed post: the native post card offers no Report on this branch (`ios-native-post-report` adds `canReport`).
- The quote sheet from the feed, the custom range sheet from the feed, and paging (the feed has one page).
- An iPhone (the signed-in one is another session's), iOS 18 signed in, VoiceOver, and the largest text sizes.
