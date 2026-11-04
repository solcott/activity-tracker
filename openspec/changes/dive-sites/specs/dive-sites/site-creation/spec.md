# Spec Delta

## Purpose

How an athlete publishes a dive site from one of their own dives in the app, keeping the site's point outside their privacy zones.

## ADDED Requirements

### Requirement: Sites are created from one's own dives
The app SHALL offer to create a site only on the signed-in athlete's own dive that has a track position and no visit to a live site. The site's centre SHALL be the dive's entry position. A track that can't be read SHALL make creation say it couldn't load the position and offer to try again.

#### Scenario: Own dive at a new place
- **WHEN** an athlete opens their own dive that has an entry position and no site
- **THEN** the app offers to create a site from it

#### Scenario: Dive already at a site
- **WHEN** an athlete opens their own dive that visits an active site
- **THEN** the app doesn't offer to create a site

### Requirement: A site's centre stays outside the athlete's privacy zones
The app SHALL NOT offer creation when the entry position lies inside one of the athlete's privacy zones or within their hidden start distance, and SHALL say why. It SHALL NOT offer creation while it can't read the zones.

#### Scenario: Entry inside a zone
- **WHEN** an athlete's shore dive starts inside their home privacy zone
- **THEN** creation isn't offered, and the app says the entry is inside a privacy zone

### Requirement: The athlete names the site and chooses its radius
The athlete SHALL give the site a name of at most 100 graphemes and choose a radius between 25 m and 500 m, 100 m by default, seeing it on a map. The app SHALL prefill the name from the dive's own site name, if any.

#### Scenario: Prefilled name
- **WHEN** an athlete creates a site from a dive whose logbook named the site "Ras Mohammed"
- **THEN** the name field starts as "Ras Mohammed"

### Requirement: Duplicates and the allowance are checked first
Before publishing, the app SHALL warn that the site's position and name will be public, then ask `feed.checkDiveSite`. When the position duplicates a site it SHALL show that site and not publish; when no allowance is left it SHALL say so and not publish; when the check fails it SHALL not publish and let the athlete retry.

#### Scenario: Duplicate found
- **WHEN** the check returns an active site 20 m away
- **THEN** the app shows that site, lets the athlete open it, and doesn't publish

### Requirement: A created site is queued like other writes
Publishing SHALL queue the `feed.diveSite`, with `source` `athlete` and `license` `CC0-1.0`, through the outbox, naming its source dive only when the dive and its Map category are both Public. The app SHALL return to the dive and confirm the site will appear once processed.

#### Scenario: Site from an Only me dive
- **WHEN** an athlete publishes a site from an Only me dive
- **THEN** the queued record names no source dive
