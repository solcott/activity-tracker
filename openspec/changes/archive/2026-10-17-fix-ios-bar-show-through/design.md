# Design

## Context

`IosNavigationBar` draws the bar in Compose. With a `LocalBarBackdrop` it applies `hazeBlur(barMaterial(...))`, faded in by `material` once `scrollState.contentOffset < 0`. On iOS 26 (`usesLiquidGlass`) `barMaterial` is progressive: full blur and an 80 % surface tint down to `EDGE_FADE_START = 0.7` of the bar's height, then fading to nothing at its bottom. The back button sits in the 44 pt `CenteredBar` under the safe-area inset, so its lower part overlaps that fading band. The detail screen is not a tab root, so it uses `pinnedScrollBehavior`.

## Goals / Non-Goals

**Goals:** confirm the cause on the simulator, then make content behind the bar's controls illegible while keeping the soft edge.

**Non-Goals:** the large-title collapse at tab roots; Android, desktop and web bars; replacing Haze.

## Decisions

- **Reproduce first.** Screenshot the activity page on the iOS 26 simulator scrolled to put "Details" behind the back button, and note which of three causes it is: (a) the fade band starts too high, (b) `material` stays 0 because the list's scroll doesn't reach the bar's `scrollState` (no `nestedScroll` on the detail list), or (c) the list isn't a Haze source on this screen. The fix follows the cause; (a) is the expected one from the code.
- **For (a): push the fade below the controls.** Start the fade at the bar's bottom edge rather than 70 % into it, and let the effect extend a short distance (about 12 pt) past the bar into the content, as UIKit's scroll-edge effect does, so the controls sit over full-strength blur and tint. Alternative: raise the tint to near-opaque, which loses the iOS 26 look.
- **For (b) or (c): wire the screen up** like the feed (`nestedScroll(scrollBehavior.nestedScrollConnection)` on the list, content inside `AppScaffold`'s source), and keep the effect as is.

## Findings

- **Cause: (a), the bar's iOS 26 effect, and worse than the fade band alone.** Checked on the iPhone 17 Pro simulator (iOS 26.4). The blur fades in once scrolled, so the list's scroll does reach the bar (`nestedScroll` on `AppScaffold`, the detail screen's wiring), and the list is a Haze source. Neither (b) nor (c) applies. Two things let text through:
  - Text in the bar's last 30 % read through a light blur, and the effect ended in a visible line. On iOS Ski this was the "Public" picker row beside the back button.
  - At full strength, Haze's progressive blur left text sharp, only washed by the 80 % tint. A heading behind the back button (Night Run's "Details") and the profile tab's header ("@alice.test", "24 activities · 1 follower") both read clearly. The pre-iOS 26 material blurs fine, because it draws an opaque `backgroundColor` behind the blurred input.
- **Fix.** The effect is drawn on its own layer behind the bar, laid out 12 pt taller than the bar (`overhang`). On iOS 26 that layer is a uniform blur over an opaque `backgroundColor`, tinted as before and faded out by a `mask`. The mask is opaque down to the bar's bottom edge, then fades to clear over the 12 pt below. So it's full strength behind the title and buttons, with a soft edge into the content and no hairline. The fade start is a fraction of the layer's height (bar height ÷ (bar height + overhang)): Haze's `verticalGradient(startY = …)` and a `Brush.verticalGradient(startY = …)` tint drew no effect at all on iOS. Before iOS 26 there is no overhang and the material is unchanged.
- **Checked by screenshot** (iOS 26.4; iOS 18.4 on iPhone 16 Pro): bar clear at rest; "Details" behind the back button only a blur, on both; feed's collapsed large title; profile header hidden; settings fits the screen, so nothing scrolls under; iOS 18 material and hairline unchanged.
- **Out of scope:** the activity map is a native MapLibre view, which Haze can't blur. Scrolled under the bar it shows through, tinted, on iOS 26.

## Risks / Trade-offs

- [Extending the effect past the bar dims the first row under it] → keep the extension short and fully faded by its end; compare with a native `UINavigationController` screenshot.
- [Simulator lag seen in earlier checks] → verify with screenshots after scrolling settles.
