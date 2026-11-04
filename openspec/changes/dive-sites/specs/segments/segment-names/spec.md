# Spec Delta

## MODIFIED Requirements

### Requirement: A name proposal is a public record
A name proposal SHALL be a `feed.segmentNameProposal` record in its proposer's repo, naming the segment or dive site by URI and holding a name that's non-empty after trimming and at most 100 graphemes. The AppView SHALL ignore one in any space, one with an invalid name, and one naming a segment or dive site it hasn't indexed. A proposal naming a dive site SHALL follow `dive-sites/site-names`.

#### Scenario: Proposal written
- **WHEN** alice proposes "Wiggly Woods" for a segment
- **THEN** her repo gains a `feed.segmentNameProposal` naming the segment's URI and "Wiggly Woods"

#### Scenario: Blank name
- **WHEN** a proposal's name is only spaces
- **THEN** the AppView ignores it

#### Scenario: Proposal in a space
- **WHEN** a `feed.segmentNameProposal` appears in an athlete's `interactions` space
- **THEN** the AppView ignores it

#### Scenario: Proposal for a dive site
- **WHEN** alice proposes "Shark Alley" for a dive site
- **THEN** her repo gains a `feed.segmentNameProposal` naming the site's URI, which the AppView treats under the site naming rules
