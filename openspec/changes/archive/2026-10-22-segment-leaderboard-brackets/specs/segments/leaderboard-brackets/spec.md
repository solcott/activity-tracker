# Spec Delta

## Purpose

Defines how an athlete consents to age-group, weight-class and gender leaderboards: the brackets record their client writes, how it stays current, the bracket boundaries, and what the AppView may hold from it.

## ADDED Requirements

### Requirement: Brackets need the athlete's consent
An athlete SHALL appear on age-group, weight-class or gender boards only while they have chosen to. The choice SHALL be off by default. While it's on, an `actor.leaderboardBrackets` record SHALL exist in the athlete's `self` space, and only then; turning it off SHALL delete the record.

#### Scenario: Never chosen
- **WHEN** an athlete with matching on has never turned brackets on
- **THEN** no brackets record exists and they are on no filtered board

#### Scenario: Turned off
- **WHEN** an athlete turns brackets off
- **THEN** their brackets record is deleted

### Requirement: Only brackets leave the device
The brackets record SHALL hold only an age group, a weight class and a gender bucket, and SHALL NOT hold a birth date, weight, gender description or any other profile detail. The client SHALL compute the brackets from the athlete's own details, whichever tier they are stored in.

#### Scenario: Only me birth date
- **WHEN** an athlete whose birth date is Only me and who is 41 turns brackets on
- **THEN** the record says `35-44` and holds no date

### Requirement: Brackets use Strava's boundaries
Age groups SHALL be `0-19`, `20-24`, `25-34`, `35-44`, `45-54`, `55-64`, `65-69`, `70-74` and `75+`, by whole years of age on the day computed. Weight classes SHALL be `0-54`, `55-64`, `65-74`, `75-84`, `85-94`, `95-104`, `105-114` and `115+`, by whole kilograms rounded down. Gender buckets SHALL be `female`, `male` and `nonBinary`.

#### Scenario: Weight on a boundary
- **WHEN** an athlete weighs 64.9 kg
- **THEN** their weight class is `55-64`

#### Scenario: Birthday
- **WHEN** an athlete born on 1990-06-10 computes brackets on 2025-06-10
- **THEN** their age group is `35-44`

### Requirement: A bracket is left out when its detail is missing
A bracket SHALL be absent from the record when the athlete hasn't given its detail. The gender bucket SHALL be absent when the athlete's gender is their own description rather than one of the known values.

#### Scenario: No weight
- **WHEN** an athlete with a birth date and no weight turns brackets on
- **THEN** the record has an age group and no weight class

#### Scenario: Own gender description
- **WHEN** an athlete's gender is a description of their own
- **THEN** the record has no gender bucket

### Requirement: The client keeps the brackets current
While brackets are on, the client SHALL recompute them when the athlete saves their profile details and once per session, and SHALL rewrite the record only when a bracket changed. A rewrite that fails SHALL be tried again the next session.

#### Scenario: Turning 45
- **WHEN** an athlete in `35-44` opens the app on or after their 45th birthday
- **THEN** the record is rewritten with `45-54`

#### Scenario: Weight edit
- **WHEN** an athlete with brackets on changes their weight from 70 kg to 76 kg
- **THEN** the record's weight class becomes `75-84`

#### Scenario: Nothing changed
- **WHEN** an athlete opens the app and their brackets are as stored
- **THEN** the record isn't rewritten

### Requirement: The AppView holds brackets only from self
The AppView SHALL index the brackets record only from the athlete's `self` space, SHALL use it only to filter leaderboards, and SHALL NOT serve it. It SHALL ignore a bracket value it doesn't know. It SHALL drop what it held when the record is deleted, the `self` space is deleted or the account is deleted.

#### Scenario: Record in a repo
- **WHEN** a brackets record appears in an athlete's public repo
- **THEN** the AppView doesn't index it

#### Scenario: Turned off
- **WHEN** an athlete's brackets record is deleted
- **THEN** once the AppView indexes the delete, the athlete is on no filtered board

#### Scenario: Unknown value
- **WHEN** a record's age group is `40-49`
- **THEN** the AppView treats the athlete as having no age group

### Requirement: The brackets choice is in Settings
While the athlete's segment matching is on and their session can write the record, Settings SHALL offer showing them on age, weight and gender leaderboards, off by default, saying it reveals those brackets to anyone who can see their efforts. Saving SHALL be a direct write that reports a failure and keeps the earlier choice, and SHALL NOT change the matching or leaderboard settings.

#### Scenario: Turn on
- **WHEN** an athlete with matching on turns brackets on
- **THEN** their brackets record is written and Settings shows it on

#### Scenario: Write fails
- **WHEN** the PDS can't be reached as the athlete turns brackets on
- **THEN** Settings reports the failure and shows brackets off

#### Scenario: Matching off
- **WHEN** an athlete's matching is off
- **THEN** Settings doesn't offer the brackets choice

#### Scenario: Old session
- **WHEN** an athlete signed in before the space type listed the brackets record
- **THEN** Settings doesn't offer the brackets choice
