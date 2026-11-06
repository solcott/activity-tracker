# Spec Delta

## ADDED Requirements

### Requirement: Site proposals are shared as segment proposals are
The site screen's propose and edit sheet SHALL offer Share to feed as the segment screen's does (`segments/segment-names`), and a post sharing a site proposal SHALL follow `feed/posts` with the site in place of the segment, and a visit in place of an effort.

#### Scenario: Sharing a site name
- **WHEN** alice proposes "Shark Alley" for a dive site and shares it with Mutuals
- **THEN** a post sharing the proposal is written to her `mutuals` space, and her mutuals' timelines show it with the site's current name

#### Scenario: Diver votes from the card
- **WHEN** bob, who has a visit to the site, sees alice's post
- **THEN** the card lets him vote on her proposal
