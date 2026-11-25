---
version: 1
slug: "itytracker-app-detail-activitydetailui-kt-c2523e45"
primary_target: "feature/detail/ui/src/commonMain/kotlin/io/github/solcott/activitytracker/app/detail/ActivityDetailUi.kt"
related_targets: ["iosApp/iosApp/Screens/ActivityPage.swift"]
---

# Activity detail

Mode: Operate. Audience: the athlete reviewing their own effort and friends arriving from the feed, equally; phone first, wide windows to review. Job: see where and what it was, then read the numbers that matter for the sport, then reach any figure without wading. Constraints: Compose (Android, desktop, web) and SwiftUI each in their idiom; every section the page has today stays reachable; no lexicon change (photos carry no time or place).

## Direction contract

THESIS: The page is a summary that leads with the place and the pictures, opens the one chart that matters for this sport, and folds everything else to one line; it refuses the category's wall of equal open charts.

OWN-WORLD: The Trailhead Board as it stands: plain grey ground, white hairline cards at 16px, Ridgeline Violet on actions, selection, route and the main line, amber only on kudos, trail-sign colours only on ratings, semibold numbers leading muted labels. The signature move is the mosaic: map as the big tile, photos stacked beside it, "+N" on the last.

STORY: A viewer sees the route and photos at once, reads six figures, likes or comments, reads the lead chart (power on a ride, depth on a dive, runs on a ski day, sets in a workout), scans the strips' headlines, opens one in place, and jumps by chip or goes to All data for every stat and full table.

FIRST VIEWPORT: Phone: bar with back, sport icon and title, ⋯; author line with visibility; mosaic about 300dp (map 2/3, two photos stacked); six-figure hero card; the likes and comments block in full (the user chose comments before the charts, 2026-11-24, so the lead card starts below the fold on a phone). Wide: 400dp side pane (mosaic, header, hero, social) beside the open lead card and two-up strips.

FORM: Mosaic, position 2 on the ordered list of 6 (dealt, locked by the user over the roll's lead), with density rules from mosaic-density.html and the All data page from all-data.html. seed:94e2eee4791e9b19

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

Signature interaction: tapping a strip opens its full chart in place; tapping a section chip scrolls there and opens it. Motion grammar: the platform's own expand (animateContentSize / SwiftUI default), nothing decorative.
