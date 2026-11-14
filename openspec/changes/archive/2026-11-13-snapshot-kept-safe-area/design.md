# Design

## Context

- swift-snapshot-testing's `drawHierarchyInKeyWindow` path adds the controller to the key window at its origin, under the 62-point
  status bar, then moves it 10,000 points off screen and draws. Off screen the view has no safe area. UIKit moves a navigation bar up at
  once; SwiftUI gives the lists in a stack their new inset a run-loop turn later. Most references were recorded before that turn: the bar
  at y 10 and the list laid out as if under the status bar.
- `KeptSafeArea` (`Snapshots.swift`) holds the hosting controller as a child and sets its `additionalSafeAreaInsets` to what the window
  has and it lacks, so nothing changes when the library moves it. Only Import's `wide` uses it.

## Goals / Non-Goals

**Goals:** every snapshot drawn with the window's safe area; the references rerecorded to match and checked for drift.

**Non-Goals:** no tolerance, sample or settle-count change; no app code change.

## Decisions

- **A component takes no safe area.** Keeping the window's safe area for everything (the first try) pushed every component down 31
  points in its frame, half the status bar's 62, and cut off the bottom of short ones (the design gallery's images, the dive site's
  route map stand-in): a component never sits under the status bar. `hosting.safeAreaRegions = []` makes the view's own SwiftUI content
  ignore the safe area, so a component lays out in its whole frame, as its references already show. A navigation stack inside reads its
  safe area from UIKit, not SwiftUI (import-leftovers saw `safeAreaRegions` change nothing for it), so it still keeps the window's.
  Alternative: a per-call choice between screen and component; nothing in a call's size tells them apart (120-point chips, 820-point
  iPad pages), and the split by stack falls out of how each reads its safe area.
- **Make it unconditional, not a defaulted parameter.** No snapshot wants the stale layout, so `assertAppSnapshot` always wraps the
  hosting controller and the parameter goes. Alternative: keep `keepsSafeArea` defaulting to `true`; it would leave an option nobody
  should take.
- **Rerecord by deleting only the references that fail.** Run the whole suite with the change; delete the failing tests' references and
  run again to record them, then a third time to compare. A reference that already matched (a view with no bar or list under the status
  bar, a sheet-sized component) stays untouched.
- **Check each changed reference** against its old version: a pixel diff whose changed rows are explained by the bar and content moving
  under the status bar, with no other change.

## Risks / Trade-offs

- A snapshot whose content truly reaches under the status bar now draws the status bar's safe area as on a device; that is the intended
  look, not drift.
