# Design

## Context

`ActivityDetailPage.segments` is an `ImmutableList<EffortView>` straight from `feed.getActivitySegments`, already in start order, and `ActivitySegmentsUi.kt` draws one `EffortRow` per effort. Segments carry no parent/child links (only `duplicateOf`; the importer's way ids never reach the AppView), but every `EffortView` has `startOffset` and `elapsed`, so each effort is an interval `[s, e] = [startOffset, startOffset + elapsed]` in whole seconds on the activity's own timeline. `:app-screens` is a strict-stability module and `:app-fixtures` depends only on it and the lexicon.

## Goals / Non-Goals

**Goals:**
- Derive groups, nesting, spans and short labels purely from the efforts the client already has.
- One pure, unit-tested function shared by the presenter and the fixtures.

**Non-Goals:**
- Any lexicon, AppView or importer change (e.g. publishing parent links).
- Collapsing groups, or changing the segment screen or other effort lists.

## Decisions

**Time intervals, not geometry or names.** Containment on the activity's timeline is exactly what "this effort was part of that one" means for the viewer, works for athlete-made segments too, and needs no extra data. Name heuristics only shorten labels; they never decide grouping. Alternative (server-side parent links from importer way ids) needs lexicon + AppView + importer changes and misses athlete segments.

**Tolerance of 2 s.** Effort ends are rounded to whole seconds, so a stretch can end a second past its trail. Containment allows 2 s at each end; a parent must be more than 2 s longer than its child, so equal spans are siblings.

**Grouping needs a quarter of the shorter effort, not just 2 s.** Efforts are timed from each segment's own start line to its own end line, so segments meeting at a junction overlap by a few seconds (on the 2026-10-27 Corner Canyon ride: 3 s between Ghost Falls Trail and Corner Canyon Road, 13 s between Corner Canyon Road (part 6) and Peak View Trail). A plain 2 s threshold chained those into one group of siblings. Two efforts group when their overlap exceeds both 2 s and 25% of the shorter one; groups are the connected components of that relation. A contained effort always overlaps its container by about its whole length, so nesting is unaffected. Alternative considered: group only by containment, which would split efforts that genuinely share a stretch of trail without one containing the other.

**Algorithm (`groupEfforts`, in `:app-screens` `app/detail/`).**
1. Groups: sort by `s` asc, `e` desc, original index; union every pair that overlaps by more than 2 s and 25% of the shorter (union-find over all pairs; a few dozen efforts at most), and list the groups in the order their first effort began.
2. Parents: within a group, each effort's parent is the shortest effort that contains it within 2 s and is more than 2 s longer. Choosing the parent by containment, not by position in a start-ordered stack, matters because rounding can make a child start a second *before* its parent (observed: "Rush Trail - Limelight Section: Bonneville… to Creek View…" 1:11 timed 1 s before "Rush Trail - Limelight Section" 3:12, so the server's start order lists the child first). Strictly-longer parents rule out cycles. Groups are small (a handful of efforts), so the O(n²) scan is fine.
3. Order: emit the group's tree depth-first; roots and each node's children sorted by `s` asc, `e` desc, then original index (no tolerance here: a tolerant comparator isn't transitive). Depth = tree depth. Spans as `Float` fractions of `[groupStart, groupEnd]`, null for a group of one. Per row, `lastChild` and `guides` (for each ancestor depth, whether a later sibling at that depth follows, so its vertical guide continues) are filled in a second pass. Label: parent's trail base = name up to the first `": "`, `" ("`, `" via "`, `" from "`; strip `base + ": "` or `base + " - "` from the child's name when present (and something remains).

Living in `:app-screens` lets fixtures build realistic samples by calling it, instead of hand-built rows that could drift from the real algorithm. It is plain Kotlin over lexicon types, which that module already exposes.

**Model.** `SegmentEffortGroup(rows: ImmutableList<SegmentEffortRow>)`, `SegmentEffortRow(effort: EffortView, label: String, depth: Int, guides: ImmutableList<Boolean>, lastChild: Boolean, spanStart: Float?, spanEnd: Float?)`, both `@Immutable` data classes, satisfying strict stability. `ActivityDetailPage.segments` becomes `ImmutableList<SegmentEffortGroup>`; empty still means no section.

**UI.** Groups in the existing `AppCard`, a `HorizontalDivider` between groups (Material divider is used directly elsewhere; there's no adaptive wrapper). Each row: start padding `depth * 16.dp`, guide lines drawn in the indent with `drawBehind` in `outlineVariant` (`|-` / `'-` at the row's own depth, `|` for continuing ancestors), the existing `EffortRow` with `label`, and under it when `spanStart != null` a 4 dp rounded track in `surfaceVariant` with the `[spanStart, spanEnd]` range in `primary`. Accessibility: the bar is decorative (`clearAndSetSemantics {}`); the row keeps its click and label.

## Risks / Trade-offs

- [A route crossing the same trail at two different times can make unrelated segments overlap, e.g. an out-and-back] → they'd only group if their spans overlap in time, which on one track means the same stretch of riding; acceptable.
- [Deep or wide groups on narrow phones] → indent is 16 dp per level and imported hierarchies are 2–3 deep; names wrap as today.
- [Prefix stripping hides context] → only when the parent shows the same trail name directly above; full name on the segment screen.
- [State type change touches fixtures and tests] → all updated in the same change; screenshot references regenerated.
