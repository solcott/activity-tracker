# Proposal

## Why

The Segment page stacks every fact as its own line of body text: length, climb, high and low points, creator, credit and status. The result has no reading order. Leaderboard is a lone outlined button and the Mayor card sits apart from it, although both answer "who owns this segment". Trail details is a flat label/value list. On the Trail page that list fills the header, so on a phone the first segment row starts below the fold.

The design canvas ([Trail & Segment pages](https://claude.ai/artifact/3fh7CDY6oiG6qbripg1cVy), artboards 2–4) gives the pages a hierarchy. Later changes need that hierarchy in place first: rating colors, Trail search and filters, a trail-wide profile, leaders on Trail rows, and crowd-sourced details, photos and comments. This change is the layout alone, with no lexicon or AppView change.

## What Changes

- **Segment page order**:
  1. The map, full width, as the page's first item.
  2. A heading block: the kind and status line, the current name, the original name when renamed, and "on <trail>" links to the trails and roads it lies on.
  3. The sport and direction chips.
  4. A row of up to four figures: distance, gain, average grade and maximum grade, each only when known.
  5. The elevation profile in a card, with the high and low points in its heading.
  6. Leaderboard and Mayor as two cards side by side.
  7. Trail details grouped into cards.
  8. Names, then the viewer's efforts.
  9. A footer with the creator, the credit and the status help.

  The top bar stays the platform's own, with the title and Star as today.
- **Leaderboard card**: shows the board's fastest time and athlete, and the viewer's own time and place when they have one. It is read with the existing leaderboard query at one entry, as the Mayor card already is, and replaces the Leaderboard button. It is shown only when the page offers a leaderboard today.
- **Mayor card**: moves beside the Leaderboard card, with the same content and rules.
- **Trail details**: grouped into three cards, Difficulty, Surface & width, and Access & season, with the same rows, words and filtering by sport group as today.
- **Trail page header**: the summary line stays. Trail details collapse to a one-line summary of up to three rows that expands to the grouped cards, so the segment list starts near the top on a phone.
- **Both platforms**: Compose (Android, desktop, web) and the native iOS pages (`SegmentPage`, `PathPage`, `TrailDetailsSection`) change together. The order, groups and words are chosen in shared Kotlin, so the two UIs can't drift.
- **Out of scope**, each a later change:
  - Rating colors and the rating badge.
  - Search and filters on the Trail page.
  - A trail-wide profile.
  - Leaders on Trail rows.
  - Conditions, photos and comments.
  - Crowd-sourced details.
  - Restyling the Leaderboard and Mayors screens.

## Capabilities

### New Capabilities
- `segments/segment-page`: the Segment page's layout as a whole: what it shows, in what order, the heading with its trail links, the figure row and the boards row.

### Modified Capabilities
- `segments/segment-elevation`: gain, average grade and maximum grade move into the figure row. High and low points move into the profile card's heading. Loss shows with the gain figure.
- `segments/segment-mayors`: the Mayor card sits in the boards row beside the Leaderboard card.
- `segments/segment-leaderboards`: the segment screen shows its leaderboard's leader and the viewer's place in a card, replacing the Leaderboard button.
- `segments/trail-attributes`: Trail details are drawn as grouped cards. On the Trail page they start collapsed to a summary.
- `segments/segment-efforts`: the native segment page's requirements name the cards instead of the Leaderboard button.

## Impact

- **Contracts** (`feature/segment/api`):
  - `SegmentState` gains the leaderboard summary and the segment's trails (names and URIs).
  - `SegmentPageTexts` and `TrailTexts` gain the figure row, the trail-details groups and the Trail page summary.
  - `PathState` gains whether the details are expanded, and `PathEvent` an event to toggle them.
- **Presenters**:
  - `SegmentPresenter` reads the leaderboard's first entry and the segment's path names through the existing `SegmentRepository` (`leaderboardPage` gains a `limit`, which the lexicon already allows; `pathViews`).
  - `PathPresenter` keeps the expanded flag.
- **UIs**:
  - Compose: `SegmentUi`, `PathUi`, `TrailDetails`, and new cards in `feature/segment/ui`.
  - iOS: `SegmentPage.swift`, `PathPage.swift`, `TrailDetailsSection.swift`.
- **Tests and references**:
  - Samples in `:app-fixtures` (`SegmentSamples`, `PathSamples`).
  - UI tests in `feature/segment/ui/src/jvmTest`.
  - Presenter tests.
  - Android screenshot references.
  - iOS snapshot references (`SegmentSnapshotTests`, the Trail page's).
- **No changes** to lexicons, the AppView or stored data.
