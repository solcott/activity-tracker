# Product

<!-- impeccable:product-schema 1 -->

## Platform

adaptive

## Users
Everyday multisport athletes (running, cycling, hiking, skiing and snowboarding, swimming, strength, diving) who record or import their training, look back at it, and share it with others. They are the audience mainstream activity-tracking apps serve today, and they want an alternative where they keep their data and control who sees each part of it. They use the app on a phone while recording and right after an activity, and on a larger screen (tablet, desktop, web) to review, import files and manage gear and settings. The same athletes also use it to **find where to go**: choosing a trail, a ski run or a dive site, checking how hard it is and what shape it's in now, and contributing what they know about their local places.

## Product Purpose
Two jobs in one product, with tracking first:
1. **Track (primary).** A social activity tracker built on the AT Protocol. Athletes record (GPS, phone only) or import (FIT, GPX, TCX, dive logs) activities, get detailed activity pages (map, elevation, heart rate, power, zones, laps, multisport legs, swim lengths, strength sets, dive profiles), follow each other, like, comment and repost, track gear usage, and compete on segments.
2. **Discover.** Trail and place information at the depth of dedicated trail-discovery and backcountry-mapping apps: trails, roads, segments and dive sites to browse on a map, with difficulty, attributes, recent conditions and local knowledge, kept current by the community rather than by a company.

The two feed each other: the activities athletes record are what reveal which trails get ridden, what condition they're in and what locals call them.

Success means an athlete can move their whole training life here from a mainstream tracker without losing analysis depth or the social layer, and can plan where to go without a second app, all without giving up ownership or privacy.

## Positioning
- **You own your data.** Every activity, like, comment, follow, gear item and profile is a record in the athlete's own PDS on an open network. Any app can read it, and it goes with the athlete if they leave. The AppView never writes to their repository.
- **Privacy per detail.** Each activity is Public, Followers, Mutuals or Only me. Each category of detail (map, heart rate, power, start time, pace…) can be narrower still, enforced by ATProto spaces: the server serves each piece only to the viewers its tier admits.
- **Community-supported trails and places.** Trails, segments and dive sites come from OpenStreetMap and public agency data as well as from athletes. Locals keep them current: they propose and vote on names, and (direction) contribute photos, condition reports and trail info. Segments are matched against tracks in the background, with leaderboards (age, weight and gender brackets) and mayors. Tracker apps don't have the discovery depth, and discovery apps don't have the training layer. This one does both, owned by its community on an open network.
- **Every platform.** Android, iOS, web and desktop from one codebase. Each follows its own platform's conventions rather than one lowest-common-denominator look.

## Operating Context
- Recording happens outdoors on a phone (often with gloves, in sun, mid-effort); GPS recording supports pause, auto-recovery and privacy zones. Desktop and web offer manual entry and file import only.
- Activity files come from watches, bike computers and dive computers (Garmin, Shearwater, UDDF, Subsurface); several devices' recordings can be combined into one activity.
- Social interactions run on ATProto identity: handles, follows with optional approval, blocks, mutes, reports and labels (moderation).
- An operator admin site pins segment statuses and names.

## Capabilities and Constraints
- Lexicons have no floats; all units are integers. Repo records are public, so private data never goes in one.
- Non-public data lives in the athlete's tier spaces; visibility logic is the product's core promise and must never be weakened for a design.
- Maps are MapLibre on Android, iOS and desktop; web still draws a Canvas route preview until MapLibre Compose ships wasmJs.
- Terminology: activity, athlete, segment, effort, leaderboard, mayor, gear, tier/visibility (Public, Followers, Mutuals, Only me), detail category, post, repost, quote, dive site, trail/path.
- **Discovery today:** Trails & roads browse and the Trail page (a trail's segments, length, data credit), path filters, trail attributes and difficulty ratings (Easiest to Extreme from IMBA, MTB, SAC or piste scales, in trail-sign colours), segment and dive-site browse on a map, dive-site conditions aggregated only from dives the viewer may see, and segment and dive-site name proposals and votes.
- **Direction, not yet built:** user photos of trails and places, trail condition reports, and user-contributed trail info. Each must follow the same rules as everything else: contributions are records the contributor owns, conditions derived from activities respect each activity's tiers, and photos go through moderation and labels.
- **Undecided:** the product name. "Activity Tracker" is a working name; do not treat it as a brand.
- **Status:** pre-launch; no public users yet.

## Brand Commitments
- The name is undecided ("Activity Tracker" is a working name).
- The brand palette is "High country": violet primary with amber accents for likes and highlights (chosen 2026-11-21, replacing a placeholder teal). See DESIGN.md for values.
- The brand's primary colour must not be orange: that colour is strongly associated with an incumbent activity-tracking app, and this product must not read as a copy of it.

## Evidence on Hand
- Screenshot-test references for every screen, rendered from fixture data: `androidApp/src/screenshotTestDefaultDebug/reference/`.
- Sample states for every screen in `:app-fixtures` (`app/fixtures`).
- Sample dive logs in `samples/dive-logs`.
- Living capability specs in `openspec/specs` and the reasoning behind each change in `openspec/changes/archive`.
- Absent: real users, testimonials, usage numbers, press, pricing, app-store listings. Future work must not fabricate any of these.

## Product Principles
1. **Ownership is visible, not just technical.** Where data lives and who can see it should be legible to an athlete without them knowing what a PDS is.
2. **Privacy is never a downgrade.** Narrower visibility hides exactly what it says and nothing more; the experience stays complete for what each viewer may see.
3. **Tracking first, discovery close behind.** Recording and reviewing training is the core loop; trail and place discovery grows from it and must never crowd it out.
4. **Native on every platform.** Each client follows its platform's conventions; consistency lives in content and behavior, not identical pixels.
5. **Community over company.** Trails, names, conditions, photos and rankings come from public data and the athletes who use those places; the app's job is to make contributing easy and credit it.
