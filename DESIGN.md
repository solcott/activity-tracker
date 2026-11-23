---
name: Activity Tracker
description: Track your training and find where to go, natively on every platform.
colors:
  ridgeline-violet: "#5B3FC4"
  ridgeline-violet-dark: "#CABEFF"
  ridgeline-violet-container: "#E6DEFF"
  ridgeline-violet-container-dark: "#4929B2"
  dusk-slate: "#605B71"
  dusk-slate-container: "#E6DFF9"
  blaze-amber: "#7C5800"
  blaze-amber-dark: "#F5B83D"
  blaze-amber-container: "#FFDEA8"
  paper-grey: "#FAFAFA"
  card-white: "#FFFFFF"
  surface-container: "#EEEEEE"
  ink: "#1C1C1C"
  ink-muted: "#474747"
  outline: "#757575"
  outline-variant: "#C6C6C6"
  night-ground: "#141316"
  night-container: "#201F23"
  night-ink: "#E6E1E6"
  night-outline-variant: "#48454E"
  error: "#BA1A1A"
  error-dark: "#FFB4AB"
  trail-easiest: "#FFFFFF"
  trail-easiest-casing: "#424242"
  trail-easy: "#2E7D32"
  trail-intermediate: "#1565C0"
  trail-difficult: "#111111"
  trail-unrated: "#757575"
  route-start: "#43A047"
  route-end: "#E53935"
  route-break: "#FFA000"
typography:
  display:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "34px"
    fontWeight: 600
    lineHeight: "40px"
    letterSpacing: "-0.3px"
  headline:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "22px"
    fontWeight: 600
    lineHeight: "28px"
    letterSpacing: "-0.2px"
  title:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "15px"
    fontWeight: 600
    lineHeight: "22px"
  body:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: "20px"
  label:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "12px"
    fontWeight: 500
    lineHeight: "16px"
rounded:
  xs: "4px"
  sm: "6px"
  md: "8px"
  lg: "12px"
  xl: "16px"
  full: "9999px"
spacing:
  hairline: "2px"
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "24px"
  xxl: "32px"
components:
  button-primary:
    backgroundColor: "{colors.ridgeline-violet}"
    textColor: "{colors.card-white}"
    rounded: "{rounded.full}"
    padding: "10px 24px"
    height: "40px"
  button-secondary:
    backgroundColor: "{colors.paper-grey}"
    textColor: "{colors.ridgeline-violet}"
    rounded: "{rounded.full}"
    padding: "10px 24px"
    height: "40px"
  button-text:
    textColor: "{colors.ridgeline-violet}"
    rounded: "{rounded.full}"
    padding: "10px 12px"
  card:
    backgroundColor: "{colors.paper-grey}"
    rounded: "{rounded.xl}"
    padding: "16px"
  card-ios:
    backgroundColor: "{colors.card-white}"
    rounded: "{rounded.lg}"
    padding: "16px"
  chip-filter:
    backgroundColor: "{colors.paper-grey}"
    textColor: "{colors.ink-muted}"
    rounded: "{rounded.md}"
    height: "32px"
  chip-filter-selected:
    backgroundColor: "{colors.dusk-slate-container}"
    textColor: "{colors.ink}"
    rounded: "{rounded.md}"
    height: "32px"
  text-field:
    backgroundColor: "{colors.paper-grey}"
    textColor: "{colors.ink}"
    rounded: "{rounded.xs}"
    padding: "16px"
---

# Design System: Activity Tracker

## Overview

**Creative North Star: "The Trailhead Board"**

At a trailhead you need the map board to be clear, signed and trustworthy: what's here, how hard it is, where you are. Nothing on it is decorative, and its one loud vocabulary, the green, blue and black of trail signs, is loud because it carries meaning. This system works the same way. The athlete's data (routes, splits, heart rate, segments, ratings) is the content. The chrome around it is calm, neutral and each platform's own, and colour shows up where it says something: the brand violet on actions and selection, amber on kudos, trail-sign colours on difficulty, and a fixed palette for zones and grades.

The system is **adaptive, not uniform**. Android draws Material 3 with Roboto at Material's sizes. Desktop and web draw the same Compose UI with bundled Inter, a denser type scale, tighter corners and 40px touch targets for pointers. iOS draws every screen natively in SwiftUI with SF and system controls, tinted with the same brand colour. Consistency lives in colour, content, terminology and behaviour, never in identical pixels.

The components are native but should grow **sturdy**, like well-made outdoor gear: confident fills on the one main action, clear weights, generous hit areas, and surfaces that read as solid rather than airy. Today they're stock platform controls. That's the floor to build up from, not a ceiling.

**The brand palette is "High country":** Ridgeline Violet with Blaze Amber, dusk over a ridgeline. It replaced the placeholder teal because it stays clear of every semantic palette (trail-sign green and blue, zones, grades, route markers) and of orange. The roles stay as they were: one primary, one warm accent for kudos, plain neutrals.

**Key Characteristics:**
- Data first; chrome recedes into each platform's native idiom.
- One brand colour for actions and selection, one warm accent for kudos.
- Trail-sign colours are the system's only loud colour vocabulary, and they always mean difficulty.
- Flat, outlined surfaces with tonal layering instead of drop shadows.
- Same roles in light and dark; dark is a first-class scheme, not an inversion.
- Layout follows window width (Compact, Medium, Expanded), never the platform.

## Colors

The "High country" violet-and-amber brand over plain grey neutrals, beside fixed semantic palettes (trail ratings, zones, grades, route markers) that stay the same whatever the brand becomes. Every role is a full Material 3 tonal set in light and dark, generated from HCT tonal palettes of the violet (hue 294.5) and the amber (hue 81.4) (`ui/designsystem/.../theme/Color.kt`). The iOS asset catalog mirrors the brand (`AccentColor`, `LikeColor`) and the semantic palettes (`TrailRating*`, `GradeBand*`).

### Primary
- **Ridgeline Violet** (light `ridgeline-violet`, dark `ridgeline-violet-dark`): the main action, selection, focus, links, the active tab, the main chart line and the athlete's own route line. Its container tone fills selected and emphasised areas, such as the active sidebar item. On iOS it's the app's `AccentColor`, the tint for every system control, with `OnAccentColor` for labels on it. Keep it clearly violet: drifting toward blue would collide with the Intermediate trail line.

### Secondary
- **Dusk Slate** (`dusk-slate`, `dusk-slate-container`): a desaturated violet-grey for selected chips, secondary containers, a chart's secondary line and quiet emphasis that shouldn't compete with the primary.

### Tertiary
- **Blaze Amber** (light `blaze-amber`, dark `blaze-amber-dark`): likes and highlights, and a chart's first comparison line. Light mode uses the dark amber because the bright one can't be read on light surfaces. On iOS it's `LikeColor`.

### Neutral
- **Paper Grey** (`paper-grey`): the light background and surface. Plain grey, deliberately not violet-tinted.
- **Card White** (`card-white`): the lowest surface container, and iOS grouped cards on light.
- **Surface Container** (`surface-container`): the middle of the tonal ladder for sheets, bars and wells (`#F4F4F4` low to `#E2E2E2` highest).
- **Ink** (`ink`) and **Ink Muted** (`ink-muted`): primary and secondary text.
- **Outline** (`outline`) and **Outline Variant** (`outline-variant`): field strokes and the hairline on cards and dividers.
- **Night Ground** (`night-ground`), **Night Container** (`night-container`), **Night Ink** (`night-ink`), **Night Outline Variant** (`night-outline-variant`): the dark scheme's ground, cards, text and hairlines. Dark neutrals keep a faint violet cast, unlike the plain light greys.
- **Error** (`error`, `error-dark`): validation and failure only.

### Semantic palettes (brand-independent)
- **Trail ratings** (`trail-*`): Easiest is a white line on a dark casing, Easy green, Intermediate blue, Difficult black, Extreme a double black line with a white centre, and unrated grey. Every non-Easiest line sits on a white casing so it reads on any basemap. Dark mode brightens green and blue slightly (`#43A047`, `#1E88E5`) and unrated to `#8A8A8A`. Defined once in `TrailRatingColors` and mirrored by `TrailRatingStyle.swift`.
- **Route markers** (`route-start`, `route-end`, `route-break`): green start, red end, amber pause or break.
- **Grade bands**: five steps from green to red for gradient on charts (light `#43A047 → #9E9D24 → #F57F17 → #EF6C00 → #D32F2F`, dark `#81C784 → #D4E157 → #FFD54F → #FFB74D → #E57373`).
- **Training zones**: recovery grey, then blue, green, yellow, orange, red and purple (`ZoneBars.kt`).

### Named Rules
**The Sign Colours Mean Difficulty Rule.** Green, blue and black lines with their casings are reserved for trail ratings. Never use them as decoration, category colour or brand.

**The Amber Is Kudos Rule.** The warm accent marks likes and highlights. It never becomes a second call to action.

**The No Orange Primary Rule.** The brand primary is never orange, in this palette or any future one. Orange may appear only inside semantic palettes (zones, grades, route breaks), where it carries data.

## Typography

**Android:** Roboto at Material 3's default scale.
**Desktop and web:** Inter (bundled, OFL) at a denser scale, which is what the frontmatter records.
**iOS:** SF via the system's text styles, so Dynamic Type works.

**Character:** a neutral grotesque that gets out of the way of numbers. Semibold carries hierarchy and tight negative tracking firms up the larger sizes. There's no display face: the data is the headline.

### Hierarchy (desktop and web)
- **Display** (600, 48 / 40 / 34px, line height 56 / 48 / 40px, tracking −0.5 to −0.3px): rare. Hero numbers and page-level totals.
- **Headline** (600, 30 / 26 / 22px, line height 36 / 32 / 28px, tracking −0.3 to −0.2px): page and section titles.
- **Title** (600, 20px and 15px; 500, 14px): card titles, list row titles, stat labels with weight.
- **Body** (400, 15 / 14 / 12px, line height 22 / 20 / 16px): descriptions, comments and settings summaries. Long text is capped at a readable 720px column (`ReadableMaxWidth`).
- **Label** (500, 14 / 12 / 11px): buttons, chips, axis labels, units.

### Named Rules
**The Numbers Lead Rule.** In a stat, the value is set larger and heavier than its label and unit. Labels are small and muted, so the number is what scans first.

## Layout

Screens lay out by window width, never by platform (`WithWindowWidth`):
- **Compact** (under 600dp): one column, for any phone or a narrow window.
- **Medium** (to 840dp): two columns.
- **Expanded**: a side pane beside the content. A window under 500dp tall is never Expanded, so a phone on its side is Medium.

The navigation shell follows the same widths. Android shows a bottom bar on phones and a rail on tablets and mid-width windows. Desktop and web switch to a sidebar from 840dp. iOS uses the system `TabView` with a `NavigationStack` per tab. Settings splits into a list beside its page on wide windows.

Spacing runs on a 4px base. In order of use: 8px between related items, 16px for card padding and screen gutters, 12px between groups, 4px for tight pairs, then 24px and 32px for section breaks. Text columns cap at 720px with at least 16px side padding. Density is the platform's own: 48dp touch targets on touch screens, 40dp with a pointer.

## Elevation & Depth

Flat and outlined. Depth comes from the Material tonal ladder (surface, then surface container low to highest) and a 1px Outline Variant hairline, not from drop shadows. Cards sit on the page as outlined panels. Sheets, menus and dialogs take their platform's own elevation and scrim, and nothing else adds shadow. On iOS, depth is the system's grouped-background layering (secondary grouped background on grouped background).

### Named Rules
**The Hairline Not Shadow Rule.** A container separates from its background with a 1px Outline Variant stroke or one tonal step. Don't add a custom shadow; platform overlays keep their own.

## Shapes

Shapes are rounded and soft, with tighter corners where there's a pointer.
- **Android:** Material 3's default shape scale.
- **Desktop and web:** a pointer scale of 4, 6, 8, 12 and 16px.
- **Cards:** 16px everywhere in Compose.
- **iOS:** grouped cards use 12px continuous corners.
- **Buttons:** fully rounded on Android, desktop and web, and the system's bordered shape on iOS.
- **Fields:** Material's outlined field, with a 4px corner.
- **Map lines:** round caps and joins; a 3px line. Activity routes and a selected run have no casing; trail runs sit on a 4px casing so every rating colour, Easiest's white included, reads on any basemap.

## Components

Native but sturdy. Each platform's own control, tinted with the brand. Every Compose screen builds from the `ui/adaptive` wrappers (`AppButton`, `AppCard`, `AppTextField` and the rest), never the raw Material widgets (detekt's `UseAdaptiveComponents`). Every iOS screen builds from `iosApp/iosApp/Design`.

### Buttons
- **Shape:** fully rounded pill (9999px) in Compose; the system's bordered shape on iOS.
- **Primary** (`AppButton`): a filled Ridgeline Violet pill with white label, 40px tall, 24px side padding. On iOS it's `.borderedProminent`. One per screen region: it's the main action.
- **Secondary** (`AppSecondaryButton`): an outlined pill with a violet label. On iOS it's `.bordered`.
- **Text** (`AppTextButton`): a violet label only, for tertiary actions and dialog actions. On iOS it's `.borderless`.
- **Icon** (`AppIconButton`): an icon at the minimum touch size, always with a content description, which VoiceOver and TalkBack read.
- **Disabled:** dimmed, and does nothing when tapped.

### Chips
- **Style** (`AppFilterChip`): Material's filter chip, with an 8px corner and an outline at rest.
- **State:** selected fills with Dusk Slate Container and shows a check. Used for feed, segment and trail filters. On iOS, menu chips (`AppMenuChip`) open a picker.

### Cards / Containers
- **Corner Style:** 16px (Compose) and 12px continuous (iOS).
- **Background:** the surface, with a 1px Outline Variant border in Compose, and secondary grouped background on iOS.
- **Shadow Strategy:** none (see Elevation & Depth).
- **Internal Padding:** 16px. Content pads inside the card; the card clips it to its shape.

### Inputs / Fields
- **Style** (`AppTextField`): Material's outlined text field, with the label floating in the stroke and supporting text below. On iOS, `AppTextField` shows a label, the field and explanatory text.
- **Focus:** the stroke thickens and turns Ridgeline Violet.
- **Error:** the stroke and supporting text turn Error. The text says what's wrong ("Enter a number").

### Lists
- **Style** (`AppListSection`, `AppListRow`): a titled group of rows under a heading in the brand colour. A row has a title, an optional summary of its value, and a chevron when it opens something or a trailing control. On iOS, form rows keep the picker or toggle on one line at the trailing edge while the label wraps.

### Navigation
- **Style:** platform shells. Material bottom bar or rail on Android, a sidebar on wide desktop and web windows, and the system tab bar on iOS. The active destination takes the brand colour.

### Trail Rating Line (signature)
A map line or swatch drawn in trail-sign colours on a contrasting casing: white on dark for Easiest, a white casing for the rest, and a double black line with a white centre for Extreme. The same drawing appears on maps and as a short swatch beside a rating's name in lists (`RatingSwatch`), so the legend and the map always match.

### Stat Block (signature)
A large, semibold value with a small muted label and unit, laid out in rows or grids on activity cards and detail pages. It's the most repeated pattern in the app and the main place the type hierarchy shows.

## Do's and Don'ts

### Do:
- **Do** build every Compose screen from the `ui/adaptive` wrappers and every iOS screen from `iosApp/iosApp/Design`.
- **Do** keep the brand colour on actions, selection and focus, and amber on likes and highlights.
- **Do** separate containers with a 1px Outline Variant hairline or one tonal step.
- **Do** lay out by window width (Compact, Medium, Expanded), never by platform.
- **Do** check every new colour in both light and dark schemes; the bright amber is for dark only.
- **Do** let numbers lead: values larger and heavier than their labels and units.
- **Do** give icon-only buttons a content description.

### Don't:
- **Don't** use orange as the brand primary, now or in any replacement palette.
- **Don't** use trail-sign green, blue or black for anything but difficulty ratings.
- **Don't** add custom drop shadows to cards or rows.
- **Don't** make iOS look like Material, or Android look like iOS. Each platform keeps its own controls.
- **Don't** let the violet drift toward blue, or use a purple for a chart's other lines: either would read as the brand or as a trail rating.
- **Don't** tint the light neutrals violet; they're plain grey by design.
