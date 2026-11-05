# dive-sites/site-names Specification

## Purpose

How locals rename dive sites: the segment naming process applied to sites, with visits in place of efforts.

## Requirements

### Requirement: Sites are named as segments are
Dive sites SHALL have an original name (the record's) and a current name, and SHALL be renamed by the rules of `segments/segment-names`, with a site in place of a segment and visits in place of efforts: a proposal is accepted only from an athlete with visits on at least 5 distinct local dates to an `active` site, a vote counts only while its voter has a visit, and adoption counts the distinct athletes with visits in the last 365 days.

#### Scenario: Regular proposes
- **WHEN** alice has dived an active site on 6 different dates and proposes "Shark Alley"
- **THEN** the AppView accepts the proposal

#### Scenario: Non-diver votes
- **WHEN** an athlete with no visit to the site votes on a proposal
- **THEN** the vote isn't counted

#### Scenario: Adopted
- **WHEN** a proposal on a site meets the adoption thresholds
- **THEN** every site view serves the new current name, and the record's name stays the original

### Requirement: The site screen shows and takes names
The site screen SHALL show its current and original names, name history and open proposals with totals, let an eligible viewer propose, edit or delete their proposal, and let a diver vote, as the segment screen does.

#### Scenario: Ineligible viewer
- **WHEN** a viewer with one visit opens an active site
- **THEN** the screen shows the open proposals and lets them vote, but not propose
