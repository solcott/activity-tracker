# Spec Delta

## Purpose
How the iOS navigation bar, drawn in Compose to follow screen transitions, treats content scrolled under it, so it matches UIKit's bars: clear at rest, and never showing legible content behind its title and buttons.

## ADDED Requirements

### Requirement: Clear at rest
The iOS navigation bar SHALL show the screen's own background, with no blur or hairline, while nothing is scrolled under it.

#### Scenario: Page at the top
- **WHEN** an activity page is opened and not scrolled
- **THEN** the bar shows no blur, tint or hairline

### Requirement: Content under the bar is not legible behind its controls
Once content scrolls under the iOS navigation bar, text passing behind the bar's back button, title or actions SHALL NOT be legible, on iOS 26 and on earlier versions.

#### Scenario: Section heading under the back button
- **WHEN** an activity page is scrolled so its "Details" heading sits behind the back button
- **THEN** the heading is blurred and washed into the bar, not readable

#### Scenario: Before iOS 26
- **WHEN** the same page is scrolled on iOS 18
- **THEN** the bar's material hides the heading behind it, with a hairline at its bottom

### Requirement: Soft edge toward the content
On iOS 26 the bar's effect SHALL fade out at its bottom edge into the unblurred content, rather than ending in a hard line.

#### Scenario: Scrolled list on iOS 26
- **WHEN** a list is scrolled under the bar on iOS 26
- **THEN** the blur fades out at the bar's bottom edge with no hairline
